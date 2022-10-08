def smtlib
def targetEnv
def IIS_HOSTNAME
def TOMCAT_USERNAME
def CI_USERNAME
def CI_CREDENTIALS
def ARTIFACT_TYPE = "asf"
def EMAIL_ADDRESS = "yifei.wang@vwfsag.com"
def CURRENT_STAGE
def ERROR_MESSAGE
def ERROR_DETAILS
def ALERT_NAME



pipeline {
    agent { label 'master' }
    environment {
        DEPLOY_WORK_DIR = "$WORKSPACE/ASF/deploy/${env.BUILD_NUMBER}"
    }
    parameters {
        string name: 'APPLICATION_VERSION', defaultValue: 'latest', description: 'The version of application which to be deployed.'
        booleanParam name: 'BACKUP', defaultValue: true, description: 'Check this if you want to backup the components.'
        booleanParam name: 'CONFIGONLY', defaultValue: false, description: 'Check this if you only want to deploy the configuration.'
    }

    stages {
        stage ('Checkout Scripts') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        checkout scm
                        // load common methods
                        smtlib = load "$WORKSPACE/Library.groovy"
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
                        IIS_HOSTNAME = props.getProperty("iis.server")
                        IIS_DEPLOY_DIR=props.getProperty("iis.deploy.dir")
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
        stage ('Stop Monitoring') {
            when { expression { targetEnv.toLowerCase() == 'prod' } }
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def alertnameArr="${ALERT_NAME}".split(',')
                        for (def alertname : alertnameArr) {
                        echo "Stopping Monitoring on ${IIS_HOSTNAME}..."
                        smtlib.stopMonitor(alertname, IIS_HOSTNAME)
                        }
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
        stage ('Upload Package to Remote Server') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def currentDate = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        def iisHostArr = "${IIS_HOSTNAME}".split(',')
                        for (def iisHost : iisHostArr) {
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]){
                            sh """
                            echo Create deploy directory if not exist
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "if (!(Test-Path -Path ${IIS_DEPLOY_DIR})){New-Item -ItemType directory -Path ${IIS_DEPLOY_DIR}}"'
                            echo Uploading deploy script and package to ${iisHost}
                            scp -i ${pem} -r ${env.DEPLOY_WORK_DIR}/*.zip ${WORKSPACE}/Deploy.ps1 ${username}@${iisHost}:"${IIS_DEPLOY_DIR}"
                            echo Uncompress deploy package on ${iisHost}
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "Expand-Archive -Path ${IIS_DEPLOY_DIR}\\*.zip -DestinationPath ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}  -Force"'
                            echo This deployment include below component on ${iisHost}:
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "Get-ChildItem ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}"'
                            """
                        }
                        }
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        stage ('Stop IIS of Remote Server') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def currentDate = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        def iisHostArr = "${IIS_HOSTNAME}".split(',')
                        for (def iisHost : iisHostArr) {
                            echo "Stoping the IIS on ${iisHost}..."
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]){
                            sh """
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}; StopIIS}"'
                            """
                        }
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
                        def iisHostArr = "${IIS_HOSTNAME}".split(',')
                        for (def iisHost : iisHostArr) {
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]) {
                                //Create backup directory on remote server
                                sh """
                                if [[ ${BACKUP} == "true" ]];then
                                echo Creating archive on ${iisHost}
                                ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}; Backup}"'
                                fi
                                if [[ ${CONFIGONLY} == "false" ]];then
                                echo Deploy new package on ${iisHost}
                                ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}; Deploy}"'
                                fi
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
        stage ('Deploy Configuration File') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def currentDate = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        def iisHostArr = "${IIS_HOSTNAME}".split(',')
                        for (def iisHost : iisHostArr) {
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]) {
                                //Create backup directory on remote server
                                sh """
                                if [[ -d ${env.WORKSPACE}/configuration ]];then
                                echo Upload configuration file to ${iisHost}
                                scp -i ${pem} -r ${env.WORKSPACE}/configuration ${username}@${iisHost}:"${IIS_DEPLOY_DIR}"
                                echo Deploy configuration file on ${iisHost}
                                ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}\\configuration\\${targetEnv}; DeployConfig}"'
                                fi
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
        stage ('Start IIS of Remote Server') {
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def currentDate = sh(returnStdout: true, script: 'date +%Y%m%d').trim()
                        def iisHostArr = "${IIS_HOSTNAME}".split(',')
                        for (def iisHost : iisHostArr) {
                            echo "Starting IIS on ${iisHost}..."
                            withCredentials([sshUserPrivateKey(credentialsId: "${CI_CREDENTIALS}",keyFileVariable: 'pem',usernameVariable: 'username')]){
                            sh """
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}\\${currentDate}${env.BUILD_NUMBER}; StartIIS}"'
                            echo Remove the uploaded package on ${iisHost}
                            ssh -i ${pem} ${username}@${iisHost} 'powershell -command "& {. ${IIS_DEPLOY_DIR}\\Deploy.ps1 ${IIS_DEPLOY_DIR}; Clean}"'
                            """
                        }
                        }
                    } catch (Exception e) {
                        ERROR_MESSAGE = e.getMessage()
                        ERROR_DETAILS = e
                        throw e
                    }
                }
            }
        }
        stage ('Start Monitoring') {
            when { expression { targetEnv.toLowerCase() == 'prod' } }
            steps {
                script {
                    CURRENT_STAGE = env.STAGE_NAME
                    try {
                        def alertnameArr="${ALERT_NAME}".split(',')
                        for (def alertname : alertnameArr) {
                        echo "Starting Monitoring on ${IIS_HOSTNAME}..."
                        smtlib.startMonitor()
                        }
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
                smtlib.chatNotify(CURRENT_STAGE, ERROR_MESSAGE, CHAT_CHANNEL)
                smtlib.emailNotify(CURRENT_STAGE, ERROR_DETAILS, EMAIL_ADDRESS)
            }
        }
    }
}