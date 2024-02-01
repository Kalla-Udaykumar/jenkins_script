pipeline {
    agent {
        node {
            label 'ESC-DOCKER-UB16'
        }
    }
    environment {
        BDSERVER = "https://amrprotex008.devtools.intel.com"
        NFS_TOOLS_INSTALLER = "/nfs/png/disks/ecg_es_disk2"
        CMD_DOCKER_RUN ="docker run --rm -t -v ${NFS_TOOLS_INSTALLER}:/build/tools \
                        -e REQUESTS_CA_BUNDLE='/etc/ssl/certs/ca-certificates.crt' \
                        -v /usr/local/share/ca-certificates/:/usr/local/share/ca-certificates/ -v /etc/ssl/certs/:/etc/ssl/certs/ \
                        -v /var/lib/ca-certificates/:/var/lib/ca-certificates/ \
                        -e PKG_CONFIG_PATH=/usr/lib64/pkgconfig:$PKG_CONFIG_PATH \
                        -e LOCAL_USER_ID=`id -u` \
                        -e LOCAL_GROUP_ID=`id -g` -v ${WORKSPACE}:${WORKSPACE} -w ${WORKSPACE} \
                        --name ${BUILD_TAG} amr-registry.caas.intel.com/esc-devops/plat/lin/gvp/obs/ubuntu1404:20220913_0240"
    }
    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '90', artifactDaysToKeepStr: '30'))
        skipDefaultCheckout()
    }
    parameters {
        booleanParam(name: 'CLEANWS', defaultValue: true, description: 'Clean workspace')
        booleanParam(name: 'EMAIL', defaultValue: true, description: 'Email notification upon job completion')
        booleanParam(name: 'PUBLISH', defaultValue: true, description: 'Artifacts deployment')
    }
    stages {
        stage ('CLEAN') {
            when {
                expression { params.CLEANWS == true }
            }
            steps {
                deleteDir()
            }
        }
        stage('SCM') {
            steps {
                parallel(
                    "gvp_obs-cov": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.services.gvp.observability']],
                        branches: [[name: 'dev-stable/iti-proto']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir:'gvp_obs_cov'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])

                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.jenkins.cac.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'henosis'],
                        [$class: 'ScmName', name: 'henosis'],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    },
                    "Protex-gvp_obs_sw": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.services.gvp.observability']],
                        branches: [[name: 'dev-stable/iti-proto']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir:'protex-gvp_obs_sw'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    },
                    "esc-engservices": {
                        checkout changelog: false, scm: ([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.henosis.build.automation.services.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'esc-engservices'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])

                        checkout changelog: false, scm: ([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/Kalla-Udaykumar/jenkins_script.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'henosis'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    }
                )
            }
        }
        stage('QA: COVERITY') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'BuildAutomation', passwordVariable: 'BDPWD', usernameVariable: 'BDUSR')]) {
                    dir("${WORKSPACE}") {
                        sh """#!/bin/bash -xe
                        mkdir -p ${WORKSPACE}/init_dir
                        cp -r ${WORKSPACE}/henosis/Jira-46331/covreport.yml ${WORKSPACE}/
                        ${CMD_DOCKER_RUN} bash -c "export PATH=${PATH}:/build/tools/engsrv/tools/coverity-analyze/bin:/build/tools/engsrv/tools/coverity-reports/bin&& export HOME=${WORKSPACE} && export LAB_PASS=${BDPWD} && cd ${WORKSPACE}/gvp_obs_cov && ls -lrt && cmake --version && mkdir build && cd build && cmake .. && \
                        cov-configure --template --compiler cc --comptype gcc
                        cov-configure --template --compiler c++ --comptype gcc
                        cov-build --dir ${WORKSPACE}/init_dir/ make
                        cov-analyze --dir ${WORKSPACE}/init_dir/ --concurrency --security --rule --enable-constraint-fpp --enable-fnptr --enable-virtual
                        cov-manage-emit --dir ${WORKSPACE}/init_dir/ list
                        cov-commit-defects --user ${BDUSR} --password ${BDPWD} --dir ${WORKSPACE}/init_dir/ --url https://coverityent.devtools.intel.com/prod5 --stream GEN_LIN_GVP_QA_DLY
                        cov-generate-cvss-report ${WORKSPACE}/covreport.yml --output ${WORKSPACE}/GEN_LIN_GVP_CVSS.pdf --report --password env:LAB_PASS
                        cov-generate-security-report ${WORKSPACE}/covreport.yml --output ${WORKSPACE}/GEN_LIN_GVP_Security.pdf --password env:LAB_PASS"
                        """
                    }
                    }
                }       
            }
        }
        stage('QA') {
            parallel {
                stage ('QA: PROTEX') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'BuildAutomation', passwordVariable: 'BDPWD', usernameVariable: 'BDUSR')]) {
                            echo "STEP_TOOL: PROTEX SCANNING"
                            dir("${WORKSPACE}/esc-engservices/tools/protex") {
                                withEnv(["PATH=" + env.PATH + ":/nfs/png/disks/ecg_es_disk2/engsrv/tools/protexIP/bin"]) {
                                sh """python -u bdscan.py --verbose --name "gvp_observability" --path "${WORKSPACE}/protex-gvp_obs_sw" --cos ${WORKSPACE}/esc-engservices/tools/protex --obl ${WORKSPACE}/esc-engservices/tools/protex """
                                stash name: "protex_reports",includes: "*.xlsx"
                                }
                            }
                        }
                    }
                }
            }
        }
        stage("QA: VIRUS SCAN"){
            steps {
                script {
                    dir("${WORKSPACE}") {
                    sh """#!/bin/bash -xe
                        rm -rf upload
                        mkdir upload
                        cp -r ${WORKSPACE}/init_dir/output/summary.txt ${WORKSPACE}/upload
                    """
                    }
                    dir("${WORKSPACE}/upload"){
                        unstash "protex_reports"
                        sh "docker run --rm -e SCAN_PATH=/upload -e LOG=virus-scan-report.txt -v ${WORKSPACE}/upload:/upload \
                        amr-registry.caas.intel.com/esc-devops/utils/uvscan/linux/uvscan-app:20210726_0951"
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                // To trigger Log Parser build to push Build log to Splunk Server.
                build job: 'iotgdevops01/ADM-LOG_PARSER',
                parameters: [ stringParam(name: 'JOB_RESULT', value: "${currentBuild.result}"),
                stringParam(name: 'BUILD_URL', value: "${env.BUILD_URL}"), booleanParam(name: 'SPLUNK', value: true)
                ], wait: false, propagate: false
                if (params.EMAIL == true) {
                    emailext body: '${SCRIPT, template="managed:pipeline.html"}', subject: '$DEFAULT_SUBJECT', replyTo: '$DEFAULT_REPLYTO', to: '$DEFAULT_RECIPIENTS, tauseef.ahmed@intel.com'
                }
            }
        }
    }
}
