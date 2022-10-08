def smtlib
def targetEnv
def TOMCAT_HOSTNAME
def TOMCAT_USERNAME
def CI_USERNAME
def CI_CREDENTIALS
def ARTIFACT_TYPE = "smt"
def EMAIL_ADDRESS = "yifei.wang@vwfsag.com"
def CURRENT_STAGE
def ERROR_MESSAGE
def ERROR_DETAILS
def ALERT_NAME

pipeline {
    agent { label 'master' }
    environment {
        DEPLOY_WORK_DIR = "$WORKSPACE/SMT/deploy/${env.BUILD_NUMBER}"
    }

    stages {
        stage ('Checkout Scripts') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        checkout scm
                        // load common methods
                        smtlib = load "$WORKSPACE/SMTLibrary.groovy"
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        stage ('Read Configuration') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        // get environment information,make sure the pipline name is like XXX_prod or XXX_cons
                        targetEnv = "${env.JOB_NAME}".split('_')[1]
                        // read configuration file inforamation base on environment
                        def props = smtlib.readConfig(targetEnv)
                        // set variables
                        TOMCAT_HOSTNAME = props.getProperty("tomcat.app.server")
                        TOMCAT_USER_HOME=props.getProperty("tomcat.user.home.dir")
                        TOMCAT_WEBAPP_PATH=props.getProperty("tomcat.app.webapp.dir")
                        CI_CREDENTIALS = props.getProperty("ci.credentials")
                        ALERT_NAME = props.getProperty("alert.name")
                        CHAT_CHANNEL = props.getProperty("jenkins.channel")
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        
        stage ('Deploy Package') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def currentDate = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        def tomcatHostArr = "${TOMCAT_HOSTNAME}".split(',')
                        for (def tomcatHost : tomcatHostArr) {
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]) {
                                sh """
                                    ls -ltr ${WORKSPACE}/configuration/${targetEnv}
                                    echo deploy configuration file on ${tomcatHost}
                                    scp -i ${pem} -r ${WORKSPACE}/configuration/${targetEnv}/webapps ${username}@${tomcatHost}:~/tomcat-smt-service
                                    """
                            }
                            }
                        }
                        catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        
        stage ('CleanUp') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                    //Clean the temporaty file in Jenkins server
                    echo "Clean up the deploy file directory in Jenkins server"
                    sh "[[ -d ${env.DEPLOY_WORK_DIR} ]] && rm -r ${env.DEPLOY_WORK_DIR}|| exit"
                
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }

     stage ('Verification') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def hasError = false
                        def tomcatHostArr = "${TOMCAT_HOSTNAME}".split(',')
                        for (def tomcatHost : tomcatHostArr)  {
                                 withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]){
                                def curlCmd = """ssh -i ${pem} ${username}@${tomcatHost} 'curl -X GET -o /dev/null -w "%{http_code}" -s http://${tomcatHost}:8831/management-1.0/api/sys/resource/list'"""
                                println(curlCmd)
                                def responseCode =  sh script: curlCmd, returnStdout: true
                                if (responseCode != "200") {
                                    hasError = true
                                    echo "ERROR! Cannot access instance on ${tomcatHost}, URL: http://${tomcatHost}:8831/management-1.0/api/sys/resource/list"
                                }

                        }
                        }
                        if (hasError) {
                            echo "Verification Failed."
                            currentBuild.result = "FAILURE"
                        } else {
                            echo "Verification Completed."}
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
    }
    post {
        success {
            script {
                smtlib.chatNotify(null, null, CHAT_CHANNEL)
                smtlib.emailNotify(null, null, EMAIL_ADDRESS)
            }
        }
        failure {
            script {
                //Clean the temporaty file in Jenkins server if job failed
                echo "Clean up the deploy file directory in Jenkins server"
                sh "[[ -d ${env.DEPLOY_WORK_DIR} ]] && rm -r ${env.DEPLOY_WORK_DIR}|| exit"
                sh "[[ -d ${WORKSPACE}/configuration ]] && rm -r ${WORKSPACE}/configuration|| exit"
                smtlib.chatNotify(CURRENT_STAGE, ERROR_MESSAGE, CHAT_CHANNEL)
                smtlib.emailNotify(CURRENT_STAGE, ERROR_DETAILS, EMAIL_ADDRESS)
            }
        }
    }
}