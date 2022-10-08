
import java.text.SimpleDateFormat
import java.util.Calendar
def copyCutoffFiles(def username, def hostname) {
    sh """
        echo "Now start to process host ${hostname}"
        scp /tmp/text.txt ${username}@${hostname}:/tmp
    """
}

/**
 * @description: 
 * @param {username} {hostname}
 * @return: 
 */
def backupCutoffFiles(def username, def hostname, def jboss_username) {
    sh """
        echo "calling method backupCutoffFiles on host ${hostname}"
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} sudo sh /${jboss_username}/backup_cutoff_files.sh
        echo "The last 10 items of backup folder on ${hostname}, please check"
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} ls -l /${jboss_username}/backup | tail -n 10
    """
}

/**
 * @description: 
 * @param {username} {hostname}
 * @return: 
 */
def startJBoss(String username, String hostname) {
    // ${JBossStatus} = this.getJBossStatus()
    // switch(${JBossStatus}) {
    //     case "JBoss is running.":
    //         echo "JBoss is already running, not necessary to start it again."
    //     break
    //     case "JBoss is not running.":
    //         sh """
    //         echo "Now start to process host ${hostname}"
    //         ssh ${username}@${hostname} "cd /${JBOSS_HOSTNAME}/jboss-as/bin;sudo sh posstart.sh"
    //         ssh ${username}@${hostname} sleep 2
    //         """ 
    //     break
    // }
    
    // ssh ${username}@${hostname} "cd /k_jboss/jboss-as/bin;sudo sh posstart.sh"
    // ssh ${username}@${hostname} "cd /"${JBOSS_HOSTNAME}"/jboss-as/bin;sudo sh posstart.sh"

    // try {
    //         sh """
    //         echo "Now start jboss on host ${hostname}"
    //         ssh ${username}@${hostname} """
    //                                         sudo -u k_jboss "nohup /k_jboss/jboss-as/bin/standalone.sh >> /k_jboss/jboss-as/standalone/log/server.log 2>&1 &"
    //                                     """
    //         ssh ${username}@${hostname} sleep 2
    //         """ 
    // }

    sh """
        echo "calling method startJBoss on host ${hostname}"
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} "sudo systemctl start jboss"
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} sleep 2
    """ 
}


/**
 * @description: 
 * @param {username} {hostname}
 * @return: 
 */
def stopJBoss(String username, String hostname) {
    // ssh ${username}@${hostname} "cd /k_jboss/jboss-as/bin;sudo sh posstop_new.sh"
    // ssh ${username}@${hostname} "cd /"${JBOSS_HOSTNAME}"/jboss-as/bin;sudo sh posstop_new.sh"

    // try {
    // sh """
    // echo "Now start to process host ${hostname}"
    // ssh ${username}@${hostname} "sudo -c k_jboss  /k_jboss/jboss-as/bin/jboss-cli.sh --connect --command=:shutdown >> /k_jboss/jboss-as/standalone/log/server.log"
    // """
    // }
    sh """
        echo "calling method stopJBoss on host ${hostname}"
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} "sudo systemctl stop jboss"
    """
}

def stopTomcat(String credentials,String hostname) {
        withCredentials([sshUserPrivateKey(credentialsId: "${credentials}",keyFileVariable: 'pem',usernameVariable: 'username')]) {
        sh """
        echo "calling method stop Tomcat on host ${hostname}"
        ssh -i ${pem} ${username}@${hostname} "sudo systemctl stop tomcat"
        """}
}

def startTomcat(String credentials,String hostname) {
    // ssh ${username}@${hostname} "cd /k_jboss/jboss-as/bin;sudo sh posstop_new.sh"
    // ssh ${username}@${hostname} "cd /"${JBOSS_HOSTNAME}"/jboss-as/bin;sudo sh posstop_new.sh"

    // try {
    // sh """
    // echo "Now start to process host ${hostname}"
    // ssh ${username}@${hostname} "sudo -c k_jboss  /k_jboss/jboss-as/bin/jboss-cli.sh --connect --command=:shutdown >> /k_jboss/jboss-as/standalone/log/server.log"
    // """
    // }
        echo "calling method stop Tomcat on host ${hostname}"
        withCredentials([sshUserPrivateKey(credentialsId: "${credentials}",keyFileVariable: 'pem',usernameVariable: 'username')]) {
        sh """
        ssh -i ${pem} ${username}@${hostname} "sudo systemctl start tomcat"
        ssh -i ${pem} ${username}@${hostname} sleep 2        
        """
        }
}

def stopMonitor(String alertvalue, String hostname) {
   def datefromCMD='TZ="UTC" date "+%Y-%m-%dT%H:%M:%SZ"'
   def datefrom=sh script: datefromCMD, returnStdout: true
   def datetoCMD='TZ="UTC" date -d "+30 minute" "+%Y-%m-%dT%H:%M:%SZ"'
   def dateto=sh script: datetoCMD, returnStdout: true
   datefrom=datefrom.trim()
   dateto=dateto.trim()
   def monitorCmd="""
    curl -k -O -insecure -X POST \
  https://nmp-alertmanagers.fs86.vwf.vwfs-ad/alertmanager/api/v2/silences \
  -H 'Content-Type: application/json' \
  -d '{
    "comment": "SMT deployment in ${hostname}",
    "createdBy": "DKX47DW",
    "endsAt": "${dateto}",
    "matchers": [
        {
            "isRegex": false,
            "name": "alertname",
            "value": "${alertvalue}"
        }
    ],
    "startsAt": "${datefrom}"
  }'
    """
    sh script: monitorCmd, returnStdout: true

}

def startMonitor() {
    sh '''
    if [ -f "./silences" ]
    then
    silenceid=$(tail -1 silences |sed 's/\\"//g'|sed 's/silenceID://g')
    echo Disable the silence
    curl --insecure -X DELETE https://nmp-alertmanagers.fs86.vwf.vwfs-ad/alertmanager/api/v2/silence/${silenceid}
    rm ./silences
    else
    echo "The silence doesn't exist"
    fi
    '''
}

def stopMonitorbak(String alertvalue, String hostname) {
    def dateFormat
    def date
    def dateAfterFiveMin
    def timeunits
    def startDate
    def toDate
    script {
    date = Calendar.getInstance();
    timeunits= date.getTimeInMillis();
    startDate=new Date()
    dateAfterDuration=new Date(timeunits + (30 * 60000));
    dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+0"));
    startDate= dateFormat.format(startDate)
    toDate =dateFormat.format(dateAfterDuration)
    }
    echo startDate
    echo toDate
    def monitorCmd="""
    curl -k -insecure -X POST \
  https://nmp-alertmanagers.fs86.vwf.vwfs-ad/alertmanager/api/v2/silences \
  -H 'Content-Type: application/json' \
  -d '{
    "comment": "SMT deployment in ${hostname}",
    "createdBy": "DKX47DW",
    "endsAt": "${toDate}",
    "matchers": [
        {
            "isRegex": false,
            "name": "alertname",
            "value": "${alertvalue}"
        }
    ],
    "startsAt": "${startDate}"
  }'
    """
    sh script: monitorCmd, returnStdout: true

}

/**
 * @description: read config from config files, environment in (prod, cons, test)
 * @param {environment} 
 * @return: {props}
 */
def readConfig(String environment) {
	Properties props = new Properties()
	File propsFile = new File("${WORKSPACE}/config/config_${environment}.properties")
	props.load(propsFile.newDataInputStream())
	return props
}

/**
 * @description: read config from config files, environment in (prod, cons, test...), configVar is property key
 * @param {environment} {configVar}
 * @return: {property}
 */
String readConfig(String environment, String configVar) {
	Properties props = new Properties()
	File propsFile = new File("${WORKSPACE}/config/config_${environment}.properties")
	props.load(propsFile.newDataInputStream())
	println(props)
	def property = props.getProperty("${configVar}")
	println(property)
	return property
}

/**
 * @description: convert config string to array
 * @param {environment} {configVar}
 * @return: {hostnameArray}
 */
String[] configToArray(String hostname) {
	def hostnameArray = hostname.split(",")
	for (int i = 0; i < hostnameArray.size(); i++) {
	    println(hostnameArray[i])
	}
	return hostnameArray
}

/**
 * @description: check md5 for properties file
 * @param {username} {hostname}  {jbossUserHome}
 * @return: 
 */
String checkMd5sum(String username, String hostname, String jbossUserHome, String propertiesFileName) {
    switch(propertiesFileName) {
        case 'third-party-config.properties':
    sh """
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} "md5sum /${jbossUserHome}/jboss-as/modules/com/wcg/configurations/main/config/thirdpartyconfig/third-party-config.properties"
    """    
        break
        case 'calms.properties':
    sh """
        ssh -o StrictHostKeyChecking=no ${username}@${hostname} "md5sum /${jbossUserHome}/jboss-as/modules/com/wcg/configurations/main/config/calms/calms.properties"
    """ 
        break
        default:
            echo 'Unknown *.properties file name detected.'
        break
    }

	}
	// return hostnameArray

/**
 * @description: get JBoss Status
 * @param {username} {hostname} 
 * @return: 
 */
def getJBossStatus(String username, String hostname) {
    sh """
        ssh ${username}@${hostname} "cd /${JBOSS_HOSTNAME};sudo sh JBossStatus.sh;"
    """
    return ${jbossStatus}
    echo "${jbossStatus}"
}

 /**
  * @description: push email to exchange server
  * @param {type} 
  * @return: 
  */
def emailNotify(def errStage = null, def errDetails = null, def toAddress = 'CN_AMS@vwfsag.com') {
    def jobEnv = "${env.JOB_NAME}".split('_')[1].toUpperCase()
    def errorSection = ""
    if (errStage?.trim() || errDetails?.trim()) {
        if (errDetails instanceof Exception) {
            def tmpErrDetails = errDetails.toString()
            def stackTrace = errDetails.getStackTrace()
            stackTrace.each { e -> tmpErrDetails += "<br>&nbsp;&nbsp;&nbsp;&nbsp;" + e.toString() }
            errDetails = tmpErrDetails
        }
        errorSection = """
            <!-- ERROR SET -->
            <table class='section'>
                <tr class='tr-title'>
                    <td class='td-title' colspan='2'>ERROR DETAILS</td>
                </tr>
                <tr>
                    <td width='10%'>Error Stage:</td>
                    <td width='90%'>${errStage}</td>
                </tr>
                <tr>
                    <td valign="top">Error Message:</td>
                    <td valign="top">${errDetails}</td>
                </tr>
            </table>
            <br>
        """
    }

    def emailBody = '''${SCRIPT, template="groovy-html.template"}''' + errorSection
    emailext mimeType: 'text/html', 
        body: "$emailBody", 
        compressLog: true, 
        subject: "[${jobEnv}] ${currentBuild.currentResult}: Job \'${env.JOB_NAME} - [${env.BUILD_NUMBER}]\'", 
        to: "$toAddress", 
        from: "JENKINS_NOTIFICATION <noreply@vwfsag.com>"
}

/**
 * @description: push message to rocket.chat
 * @param {errMessage} 
 * @return: 
 */
def chatNotify(def errStage = null, def errMessage = null, def channel = 'jenkins_message') {

    def jobEnv = "${env.JOB_NAME}".split('_')[1].toUpperCase()

    def jobBuildUser = wrap([$class: 'BuildUser']){"${BUILD_USER}"}
    // def jobBuildUserId = wrap([$class: 'BuildUser']){"${BUILD_USER_ID}"}
    // def jobBuildUserEmail = wrap([$class: 'BuildUser']){"${BUILD_USER_EMAIL}"}

    def rawStartTime = currentBuild.startTimeInMillis
    def rawEndTime = currentBuild.startTimeInMillis + currentBuild.duration
    def jobStartTime = new Date(rawStartTime)
    def jobEndTime = new Date(rawEndTime)
    def jobDuration = "${currentBuild.duration}".toDouble()/1000

    //rocketSend details, including color, emoji, title, text etc.
    def rcColor
    def rcEmoji
    def rcTitle
    switch(currentBuild.currentResult) {
    case 'SUCCESS':
        rcColor = 'green'
        rcEmoji = ':smile_jenkins:'
        rcTitle = 'Build Success'    
        break
    case 'FAILURE':
        rcColor = 'red'
        rcEmoji = ':angry_jenkins:'
        rcTitle = 'Build Failure'
        break
    default:
        rcColor = 'grey'
        rcEmoji = ':question:'
        rcTitle = 'Build Unstable'
        break
    }

    def rcMessage = "[${currentBuild.currentResult}][${jobEnv}] ${env.JOB_NAME} - #${env.BUILD_NUMBER} (<${env.BUILD_URL}|Details>)"
    def rcText = "Started By: ${jobBuildUser}\nStart Time: ${jobStartTime}\nEnd Time: ${jobEndTime}\nDuration: ${jobDuration} second(s)"
    if (errStage?.trim() || errMessage?.trim()) {
        rcText = rcText + "\nError Stage: ${errStage}\nError Message: ${errMessage}"
    }

    rocketSend channel: "$channel",
            emoji: "$rcEmoji",
            attachments: [[color: "$rcColor", title: "$rcTitle", text: "$rcText"]],
            message: "$rcMessage",
            rawMessage: true
}




/**
 * get deploy target info from jenkins job name
 * @param job name
 * @return deploy target
 */
String getDeployTargetFromJobName(String jobName){
    def deployTarget = "${jobName}".split('_')[1]
    return deployTarget
}

/**
 * Get package download url from Nexus.
 * @param jobName job name
 * @param version package version number, latest will get latest version, empty for latest
 * @return download url, package name
 */
def getNexusDownloadUrl(String jobName, String artifactType, String version) {
    def deployTarget = getDeployTargetFromJobName(jobName)
    def props = readConfig(deployTarget)

    def nexusBaseUrl = props.getProperty("nexus.base_url")
    def nexusRepo = props.getProperty("nexus.repository")
    def nexusGroupId = props.getProperty("nexus.group_id")
    def nexusArtifactId = props.getProperty("nexus." + artifactType + ".artifact_id")
    def nexusPacking = props.getProperty("nexus." + artifactType + ".packing")
     
    def nexusSearchEndpoint =  nexusBaseUrl + "/service/rest/v1/search/assets/download"
    def nexusSearchUrl = nexusSearchEndpoint + "?sort=version&repository=" + nexusRepo + "&maven.groupId=" + nexusGroupId + "&maven.artifactId=" + nexusArtifactId + "&maven.extension=" + nexusPacking

    if (version?.trim() && !version.trim().equalsIgnoreCase("latest")) {
        nexusSearchUrl = nexusSearchUrl + "&maven.baseVersion=" + version.trim()
    }

    def downloadUrl = ""
    withCredentials ([usernamePassword(credentialsId: 'NEXUS_JENKINS_USER', usernameVariable: 'MASKED_USERNAME', passwordVariable: 'MASKED_PASSWORD')]) {
        def curlCmd = "curl --user ${MASKED_USERNAME}:${MASKED_PASSWORD} --silent --write-out '%{redirect_url}' '${nexusSearchUrl}'"
        println("cURL Command: " + curlCmd)
        downloadUrl = sh script: curlCmd, returnStdout: true
        println("Response Location: " + downloadUrl)
    }

    def baseName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1)
    println(downloadUrl)
    return [downloadUrl, baseName]
}

return this