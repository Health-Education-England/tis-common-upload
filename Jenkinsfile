node {
  def serviceName="common-upload"
  def healthcheckHttpPort="8089"
  def healthcheckEndpoint="/${serviceName}/actuator/health"
  def version
  def isDeployable = false
  def branchName = null
  def changeId = null

  stage('Checkout') {
    version = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%H'").trim()
    //clone the repo
    if(env.BRANCH_NAME.startsWith('PR-')) {
      branchName = "origin/" + env.CHANGE_ID
    } else {
      branchName = env.BRANCH_NAME
    }
    println "======================"
    println "branch name: ${branchName}"
    println "======================"

    checkout([$class: 'GitSCM', branches: [[name: "${branchName}"]],
    userRemoteConfigs: [[refspec: "+refs/pull/*/head:refs/remotes/origin/*", credentialsId: '92699125-95eb-436a-a566-10d600a9d56e', url: 'git@github.com:Health-Education-England/tis-common-upload.git']]])

    //make sure we have the latest
    if(!env.BRANCH_NAME.startsWith('PR-')) {
      sh """git pull origin ${branchName}"""
    }
  }

  if (env.BRANCH_NAME != "master") {
    return
  }

  stage('Wait for docker image to be published') {
    println("Looking for an image tagged as ${version}")
    def imageTags = ""
    def sleepTime = 0
    def sleepMillis = 30000
    def timeoutLimit = 900000
    while(!imageTags.length() && sleepTime < timeoutLimit) {
      sleep(sleepMillis)
      sleepTime += sleepMillis
      try{
        imageTags = sh(returnStdout: true, script: """aws ecr describe-images --repository-name tis-common-upload --image-ids imageTag=${version} | tr -d [:space:] | grep -Po '"imageTags":\\K\\[[^\\]]*\\]'""")
      } catch (Exception e) {
        println("No image found after ${sleepTime} milliseconds : ${e}")
      }
    }
    isDeployable = imageTags.contains('"latest"')
  }

  if(!isDeployable) {
    currentBuild.result = 'ABORTED'
    return
  }

  milestone 1

  stage('STAGE') {
    sh """ansible-playbook -i ${env.DEVOPS_BASE}/ansible/inventory/stage ${env.DEVOPS_BASE}/ansible/${serviceName}.yml --extra-vars="{'versions': {'${serviceName}': '${version}'}}" """
    def httpStatus=sh(returnStdout: true, script: """sleep 10; curl -m 300 -s -o /dev/null -w "%{http_code}" ${HEALTHCHECK_SERVER_STAGE}:${healthcheckHttpPort}${healthcheckEndpoint}""").trim()
    if("200".equals(httpStatus)) {
      println "STAGE healthcheck is OK"
    } else {
      throw new Exception("health check failed on STAGE with http status: ${httpStatus}")
    }
  }
  milestone 2

  stage('Approval') {
    timeout(time:5, unit:'HOURS') {
      input message: 'Deploy to production?', ok: 'Deploy!'
    }
  }

  stage('PROD') {
        sh """ansible-playbook -i ${env.DEVOPS_BASE}/ansible/inventory/prod ${env.DEVOPS_BASE}/ansible/${serviceName}.yml --extra-vars="{'versions': {'${serviceName}': '${version}'}}" """
        //Healthcheck
        def httpStatus=sh(returnStdout: true, script: """sleep 10; curl -m 300 -s -o /dev/null -w "%{http_code}" ${HEALTHCHECK_SERVER_PROD}:${healthcheckHttpPort}${healthcheckEndpoint}""").trim()
        if("200".equals(httpStatus)) {
            println "PROD healthcheck is OK"
        } else {
            slackSend channel: '#monitoring-prod', color: 'danger', message: "Jenkins failed healthcheck of ${serviceName} on PROD after deploying build No.${env.BUILD_NUMBER}!"
            throw new Exception("health check failed on PROD with http status: ${httpStatus}")
        }
  }
}