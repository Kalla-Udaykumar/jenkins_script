#!groovy

@Library(['pipeline-info', 'abi']) _

def parallelGIO = [:]

def getNamespace() {
    props = readYaml file: 'inventory/organizations/intel-innersource/repos/frameworks/automation/star/star-2-0/repos.yml'
    namespace = props.name.join(",")
}

def getPRdetails() {
    props = readJSON file:"${WORKSPACE}/engservices/tools/github/PRdetails.json"
    sourceBranch = props[0].head.ref 
    targetBranch = props[0].base.ref
	sha_id = props[0].head.sha
    props.each{ r -> 
        repoName = props.head.repo.name
    }
    repoName = repoName.join(",")
    props.each{ p -> 
        prLIST = props.html_url
    } 
    prLIST = prLIST.join(", ")
}

def getConfig() {
    props = readYaml file: "${WORKSPACE}/henosis_devops/cac/val/star2.0/config.yml"
    gio_cmd = "${props.gio_cmd}"
}

def getTest() {
    props = readYaml file: "${WORKSPACE}/SmartTest/src/star_fw/framework_utils/rel_mgmt/tests_to_run.yaml"
    tests_to_run = props.tests
    suiterunid = []
    tests_to_run_list = []
    suiterunid_index = ""
    tests_to_run.each { t ->
        tests_to_run_list = t.split(" ")
        for ( i=0; i < tests_to_run_list.length ; i++ ) {
            if (tests_to_run_list[i] == "-suiterunid") {
                suiterunid_index = i+1
                print("suiterunid_index = " + suiterunid_index)
                suiterunid += tests_to_run_list[suiterunid_index]
                print("suiterunid = " + suiterunid)
            }
        }
    }
    suiterunid = "${suiterunid.join(',')}"
    print("All suiterunid = " + suiterunid)
    star_packages = "${props.star_packages}"
}

def GIO_TRIGGER(dynamic_giocmd) {
    return {
        script {
            sh """ #!/bin/bash -xe
                docker run --rm -v ${WORKSPACE}:${WORKSPACE} -w ${WORKSPACE}/gio_validation \
                -e LOCAL_USER_ID=`id -u` -e LOCAL_GROUP_ID=`id -g` -e LOCAL_USER="lab_bldmstr" ${GIO_DOCKER} \
                bash -c "export PATH=/home/lab_bldmstr/bin:$PATH && gio_plugin ${dynamic_giocmd}"
            """
        }
    }
}

pipeline {
   agent {
        node {
            label "BSP-DOCKER-POOL"
        }
    }
    environment {
        DATETIME = new Date().format("yyyyMMdd-HHmm")
        API_ID = credentials('github-api-token')
        GIO_DOCKER = "amr-registry.caas.intel.com/esc-apps/gio/ubuntu18.04:20210927_1110"
    }
    stages {
        stage('PR: POST-COMMENT'){
            steps{
                sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"Jenkins build is STARTED & Jenkins log: ${BUILD_URL}console\"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
            }
        }
        stage('BUILD: CLEAN') {
            steps {
                deleteDir()
            }
        }
        stage('BUILD: SCM - ESC-REPO') {
            steps {
                abi_checkout([$class: 'GitSCM',
                userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.jenkins.cac.git']],
                branches: [[name: "refs/heads/master"]],
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'henosis_devops'],
                [$class: 'ScmName', name: 'henosis_devops'],
                [$class: 'CleanBeforeCheckout']]])
                
                checkout([$class: 'GitSCM',
                userRemoteConfigs: [[credentialsId: 'BuildAutomationToken', url: 'https://github.com/intel-innersource/inventory.git']],
                branches: [[name: "refs/heads/master"]],
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'inventory'],
                [$class: 'ScmName', name: 'inventory'],
                [$class: 'CleanBeforeCheckout']]])
            }
        }
        stage('PR: FETCH-DETAILS') {
            steps {
                getNamespace()
              
                abi_checkout([$class: 'GitSCM',
                userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.henosis.build.automation.services.git']],
                branches: [[name: "refs/heads/master"]],
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'engservices'],
                [$class: 'ScmName', name: 'engservices'],
                [$class: 'CleanBeforeCheckout']]])
                
                dir("${WORKSPACE}/engservices/tools/github") {
                    withCredentials([usernamePassword(credentialsId: 'GitHub-Token', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh """
                        python3 changeset.py --user ${USERNAME} --token ${PASSWORD} --projName "${projName}" --prID "${prID}" --namespace "${namespace}"
                        cp changeset.txt ${WORKSPACE}/changeset.txt
                        """
                    }
                    script {
                        if (fileExists('changeset.txt') ){
                            archiveArtifacts artifacts: "changeset.txt"
                        }
                    }
                }
                dir("${WORKSPACE}/engservices/tools/github") {
                    withCredentials([usernamePassword(credentialsId: 'GitHub-Token', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                      sh """
                      python3 getPRdetails_sb.py --user ${USERNAME} --token ${PASSWORD} --projName "${projName}" --prID "${prID}" --namespace "${namespace}"
                      """
                    }
                }
            }
        }
        // Repo clone manifest for smartbronze script
        stage('BUILD: SCM - SCRIPT') {
            when {
                expression { fileExists("${WORKSPACE}/changeset.txt") == true  }
            }
            steps {
              abi_checkout([$class: 'GitSCM',
              userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/frameworks.automation.star.star-core.git']],
              branches: [[name: "refs/heads/master"]],
              extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'SmartTest'],
              [$class: 'ScmName', name: 'SmartTest'],
              [$class: 'CleanBeforeCheckout']]])
              
              abi_checkout([$class: 'GitSCM',
              userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/frameworks.automation.star.star-2-0.iotg-utility.git']],
              branches: [[name: "refs/heads/master"]],
              extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'frameworks.automation.star.iotg-utility'],
              [$class: 'ScmName', name: 'frameworks.automation.star.iotg-utility'],
              [$class: 'CleanBeforeCheckout']]])
              
            }
        }
        // Perform GIO Test Cases mapping with python scripts developed by Integration team
        stage('TEST: GIO TEST MAPPING') {
            when {
                expression { fileExists("${WORKSPACE}/changeset.txt") == true }
    	      }
            steps{
                getPRdetails()
                dir ("${WORKSPACE}/SmartTest/src/star_fw/framework_utils/rel_mgmt/") {
                    script {
                        sh """
                            cp ${WORKSPACE}/changeset.txt ${WORKSPACE}/SmartTest/src/star_fw/framework_utils/rel_mgmt/changeset.txt
                            docker run --rm -v ${WORKSPACE}:${WORKSPACE} -w ${WORKSPACE}/SmartTest/src/star_fw/framework_utils/rel_mgmt \
                            -e LOCAL_USER=lab_bldmstr -e LOCAL_USER_ID=`id -u` -e LOCAL_GROUP_ID=`id -g` -e PYTHONUNBUFFERED=1 \
                            amr-registry.caas.intel.com/esc-devops/val/star2.0/python3.8:20221305_1803 \
                            bash -c "pip install -U pyopenssl && python -B changeset_to_recipie_mapper.py -ic changeset.txt -rn ${repoName} -bn ${sourceBranch}"
                          """
                        if (fileExists('tests_to_run.yaml') ){
                          archiveArtifacts artifacts: "tests_to_run.yaml"
                        } else {
                            error("Build failed due to missing tests_to_run.yaml ")
                        }
                    }
                }
            }
        }
        stage('TEST: CREATE GIO VALIDATION DIR'){
            steps {
                dir ("${WORKSPACE}") {
                  sh """ #!/bin/bash -xe
                      if [ ! -d gio_validation ]; then
                          mkdir gio_validation
                      fi
                    """
                }
            }
        }
        stage('TEST: GENERATE DYNAMIC_PARAM JSON and ADDITIONAL_INFO JSON'){
            steps {
                getPRdetails()
                getConfig()
                getTest()
                script {
                    
                    sh """ #!/bin/bash -xe
                        python3 ${WORKSPACE}/engservices/tools/build/create_dynamic_param.py --suiterunid ${suiterunid} --commonfile ${WORKSPACE}/henosis_devops/cac/val/star2.0/common_param.json \
                        --newjsonfile ${WORKSPACE}/henosis_devops/cac/val/star2.0/dynamic_param.json
                    """
                    
                    String dynamicParam = readFile("${WORKSPACE}/henosis_devops/cac/val/star2.0/dynamic_param.json").replaceAll('sourceBranch',"${sourceBranch}").replaceAll('repoName',"${repoName}").replaceAll('package_names',"${star_packages}")
                    writeFile file:"${WORKSPACE}/gio_validation/dynamic_param.json", text: dynamicParam
                    def dynamicParam_json = readJSON file: "${WORKSPACE}/gio_validation/dynamic_param.json"
                    println dynamicParam_json
  
                    String additionaInfo = readFile("${WORKSPACE}/henosis_devops/cac/val/star2.0/additional_info.json").replaceAll('sourceBranch',"${sourceBranch}")
                    writeFile file:"${WORKSPACE}/gio_validation/additional_info.json", text: additionaInfo
                    def additionaInfo_json = readJSON file: "${WORKSPACE}/gio_validation/additional_info.json"
                    println additionaInfo_json
                }
            }
        }
        // Perform GIO Smart Bronze Test after mapped
        stage('TEST: FUNCTIONAL TEST - SMART BRONZE GIO TESTSUITE'){
            steps {
                getConfig()
                getTest()
                script {
                        if (tests_to_run != "false") {
                                for(int i=0; i<tests_to_run.size(); ++i){
                                    def test = tests_to_run[i]
                                dynamic_giocmd = "${gio_cmd}".replaceAll('-nowait',"") + " ${test}"
                                parallelGIO[i] = GIO_TRIGGER(dynamic_giocmd)
                            }
                            parallel parallelGIO
                        }
                }
            }
        }
    }
    post {
        always {
            script {
                sh """ #!/bin/bash -xe
                    docker run --rm -v ${WORKSPACE}:${WORKSPACE} -w ${WORKSPACE}/gio_validation \
                    -e LOCAL_USER_ID=`id -u` -e LOCAL_GROUP_ID=`id -g` -e LOCAL_USER="lab_bldmstr" ${GIO_DOCKER} \
                    bash -c "export PATH=/home/lab_bldmstr/bin:$PATH && chmod -R 777 html_result"
                """
              
                def results = pipelineResults()
		        for(stage in results.keySet()){
			        println("Stage: ${stage} || Status: ${results[stage]['result']} || Duration: ${results[stage]['duration']}")
			        sh """
				    curl -s -H "Authorization: token ${API_ID}" -H "Accept: application/vnd.github.v3+json" -X POST -d '{"state": "${results[stage]['result'].toString().toLowerCase()}", "target_url": "${BUILD_URL}console", "description": "Status: ${results[stage]['result']} || Total duration: ${results[stage]['duration']}", "context": "ci/${stage}"}' "https://api.GitHub.com/repos/${projName}/statuses/${sha_id}"
			        """
			        }
                // To trigger Log Parser build to push Build log to Splunk Server.
                build job: 'iotgdevops01/ADM-LOG_PARSER',
                parameters: [ stringParam(name: 'JOB_RESULT', value: "${currentBuild.result}"),
                stringParam(name: 'BUILD_URL', value: "${env.BUILD_URL}"), booleanParam(name: 'SPLUNK', value: true)
                ], wait: false, propagate: false
            }
        }
        success {
            getPRdetails()
            getNamespace()
            //return build success comment 
            sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"Jenkins CI Status: PASSED & Jenkins log: ${BUILD_URL}console | Blue Ocean Pipeline URL: https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/blue/organizations/iotgdevops01/${JOB_BASE_NAME}/detail/${JOB_BASE_NAME}/${BUILD_NUMBER}/pipeline & Verification Build  :  PASSED\"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
            script {
                sleep(time:100,unit:"SECONDS")
		println "Starting Auto-Merge"
                try {
                    dir("${WORKSPACE}/engservices/tools/github") {
                        withCredentials([usernamePassword(credentialsId: 'GitHub-Token', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                            sh """
                            python3 autoMerge.py --user ${USERNAME} --token ${PASSWORD} --projName "${projName}" --prID "${prID}" --approved --namespace "${namespace}"
                            """
                        }
                    }
                    //return auto-merge success
                    sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"PR list ${prLIST} AUTO-MERGE : PASSED & Jenkins log: ${BUILD_URL}console | Blue Ocean Pipeline URL: https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/blue/organizations/iotgdevops01/${JOB_BASE_NAME}/detail/${JOB_BASE_NAME}/${BUILD_NUMBER}/pipeline \"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
                }
                catch (err){
                    //return auto-merge failure
                    sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"PR list ${prLIST} AUTO-MERGE : FAILED & Jenkins log: ${BUILD_URL}console | Blue Ocean Pipeline URL: https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/blue/organizations/iotgdevops01/${JOB_BASE_NAME}/detail/${JOB_BASE_NAME}/${BUILD_NUMBER}/pipeline\"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
                    echo ("${err}")
                }
            }
        }
        failure {
            //return build failure comment
            sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"Jenkins CI Status: FAILED & Jenkins log: ${BUILD_URL}console | Blue Ocean Pipeline URL: https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/blue/organizations/iotgdevops01/${JOB_BASE_NAME}/detail/${JOB_BASE_NAME}/${BUILD_NUMBER}/pipeline & Verification Build  :  FAILED\"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
        }
        aborted {
            //return build failure comment
            sh "curl -s -H \"Authorization: token ${API_ID}\" -X POST -d '{\"body\": \"Jenkins CI Status: ABORTED & Jenkins log: ${BUILD_URL}console | Blue Ocean Pipeline URL: https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/blue/organizations/iotgdevops01/${JOB_BASE_NAME}/detail/${JOB_BASE_NAME}/${BUILD_NUMBER}/pipeline & Verification Build  :  ABORTED\"}' -H \"Content-Type: application/json\" \"${CommentUrl}\""
        }
    }
}
