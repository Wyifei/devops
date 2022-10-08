def smtlib
def targetEnv
def TOMCAT_HOSTNAME
def TOMCAT_USERNAME
def CI_USERNAME
def CI_CREDENTIALS
def ARTIFACT_TYPE = "smt"
def EMAIL_ADDRESS = "yifei.wang@vwfsag.com;Sha.Qin@vwfsag.com"
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
                        TOMCAT_APP_HOME=props.getProperty("tomcat.app.home.dir")
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

        
        stage ('Download Package') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        echo "Download '${params.APPLICATION_VERSION}' version of package from Nexus"

                        // get package download url
                        def (downloadUrl, downloadPkgName) = smtlib.getNexusDownloadUrl(env.JOB_NAME, ARTIFACT_TYPE, params.APPLICATION_VERSION)
                            withCredentials ([usernamePassword(credentialsId: 'NEXUS_JENKINS_USER', usernameVariable: 'MASKED_USERNAME', passwordVariable: 'MASKED_PASSWORD')]){
                            sh """
                                mkdir -p ${env.DEPLOY_WORK_DIR}
                                curl -sS --user ${MASKED_USERNAME}:${MASKED_PASSWORD} ${downloadUrl} -o ${env.DEPLOY_WORK_DIR}/${downloadPkgName} -v
                            """
                            }
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        
        stage ('Stop Monitoring') {
            when { expression { targetEnv.toLowerCase() == 'prod' } }
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        //def tomcatHostArr = "${TOMCAT_HOSTNAME}".split(',')
                        //call the Alert Manager API to POST silence
                        def alertnameArr="${ALERT_NAME}".split(',')
                        for (def alertname : alertnameArr) {
                        echo "Stopping Monitoring on ${TOMCAT_HOSTNAME}..."
                        smtlib.stopMonitor(alertname, TOMCAT_HOSTNAME)
                        }
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        stage ('Stop Tomcat of APP Server') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def tomcatHostArr = "${TOMCAT_HOSTNAME}".split(',')
                        for (def tomcatHost : tomcatHostArr) {
                            // call common method to stop tomcat on application server
                            echo "Stopping Tomcat on ${tomcatHost}..."
                            smtlib.stopTomcat("${CI_CREDENTIALS}", tomcatHost)
                        }
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
                                //Backup the components on remote server according to the multi parameter 
                                def components="${COMPONENT}".split(',')
                                for(def backupcomponent : components) {
                                    if (backupcomponent){
                                    sh """
                                    echo creating backup directory on ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost} 'mkdir -p ${TOMCAT_USER_HOME}/Backup/${currentDate}${env.BUILD_NUMBER}'
                                    echo backup component {$backupcomponent} on ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost}  'if [ ${backupcomponent} ]; then cp -rp ${TOMCAT_WEBAPP_PATH}/${backupcomponent} ${TOMCAT_USER_HOME}/Backup/${currentDate}${env.BUILD_NUMBER};fi'
                                    """
                                    }
                                    if (params.DELETE_COMPONENT) {
                                    sh """
                                    echo delete component {$backupcomponent} on ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost}  'if [ ${backupcomponent} ]; then rm -r ${TOMCAT_WEBAPP_PATH}/${backupcomponent};fi'
                                    """
                                    }
                                }
                                //Starting to uploade and uncompress the package on remote server
                                sh """
                                    echo delete the out date backup
                                    ssh -i ${pem} ${username}@${tomcatHost} 'find ${TOMCAT_USER_HOME}/Backup -maxdepth 1 -type d -not -newerct \$(date -d \"-6 month \" +%Y-%m-%d) |xargs rm -rf'
                                    echo uploading package to ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost} 'mkdir -p ${TOMCAT_USER_HOME}/SMT_Code/${currentDate}${env.BUILD_NUMBER}'
                                    scp -i ${pem} -r ${env.DEPLOY_WORK_DIR}/*.zip ${username}@${tomcatHost}:${TOMCAT_USER_HOME}/SMT_Code/${currentDate}${env.BUILD_NUMBER}

                                    echo deploy new package on ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost} 'unzip -o ${TOMCAT_USER_HOME}/SMT_Code/${currentDate}${env.BUILD_NUMBER}/*.zip -d ${TOMCAT_WEBAPP_PATH}'

                                    echo deploy configuration file on ${tomcatHost}
                                    scp -i ${pem} -r ${WORKSPACE}/configuration/${targetEnv}/webapps ${username}@${tomcatHost}:${TOMCAT_APP_HOME}

                                    echo remove the uploaed package on ${tomcatHost}
                                    ssh -i ${pem} ${username}@${tomcatHost} 'rm -r ${TOMCAT_USER_HOME}/SMT_Code/${currentDate}${env.BUILD_NUMBER}'

                                    echo empty the catalina.out
                                    ssh -i ${pem} ${username}@${tomcatHost} 'echo "" > ${TOMCAT_USER_HOME}//tomcat-smt-service/logs/catalina.out'

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
        stage ('Start Tomcat of APP Server') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def tomcatHostArr = "${TOMCAT_HOSTNAME}".split(',')
                        for (def tomcatHost : tomcatHostArr) {
                            // call common method to start tomcat on App server
                            echo "Starting Tomcat on ${tomcatHost}..."
                            smtlib.startTomcat("${CI_CREDENTIALS}", tomcatHost)
                        }
                        sh "sleep 50"
                    } catch (Exception e) {
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