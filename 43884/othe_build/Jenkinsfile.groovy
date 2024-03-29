#!groovy
@Library('abi@2.4.0') _

import owm.common.BuildInfo
Map buildinfo = BuildInfo.instance.data

email_receipients = 'jatin.wadhwa@intel.com'
subject = '$DEFAULT_SUBJECT'
body = '${SCRIPT, template="managed:abi.html"}'

linuxcloudName = 'gar-png-nswe-sws-ada'

def podDefinitionYaml = """
kind: Pod
apiVersion: v1
spec:
  volumes:
    - name: nfs
      persistentVolumeClaim:
        claimName: png-nfs
    - name: repo-tool
      persistentVolumeClaim:
        claimName: repotool
    - name: github-config
      secret:
        secretName: gitconfig
    - name: sshkeys
      secret:
        secretName: ssh-keys
    - name: netrc-lab
      secret:
        secretName: netrc
    - name: lab-ltoken
      secret:
        secretName: lab-ltoken
  nodeSelector:
    platform: ESC
  containers:
  - name: jnlp
    image: amr-registry.caas.intel.com/owr/iotg/labbldmstr_iotg_jnlp_lnx:latest
    env:
      - name: KUB_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: spec.nodeName
    tty: true
    imagePullPolicy: Always
    securityContext:
      runAsUser: 44051
  - name: build-environment1
    image: amr-registry.caas.intel.com/esc-devops/dev/rtoh/abi/ub20/tsn:20231226_1100
    resources:
      requests:
        cpu: "8.0"
        memory: "16Gi"
      limits:
        cpu: "9.0"
        memory: "18Gi"
    env:
      - name: KUB_NODE_NAME
        valueFrom:
          fieldRef:
            fieldPath: spec.nodeName
    tty: true
    imagePullPolicy: Always
    securityContext:
      runAsUser: 44051
    command:
    - /bin/bash
    args:
    - -c
    - sleep 36000
    volumeMounts:
    - name: nfs
      mountPath: "/build/tools"
    - mountPath: "/home/lab_bldmstr/.gitconfig"
      name: github-config
      subPath: ".gitconfig"
    - mountPath: "/home/lab_bldmstr/.ssh/"
      name: sshkeys
    - name: netrc-lab
      mountPath: "/home/lab_bldmstr/.netrc"
      subPath: ".netrc"
    - name: repo-tool
      mountPath: "/home/lab_bldmstr/bin"
    - name: lab-ltoken
      mountPath: "/home/lab_bldmstr/.klocwork/ltoken"
      subPath: "ltoken"
"""

def print_node_name() {
	println("POD running on === ${KUB_NODE_NAME} === worker machine")
}

pipeline {
    agent {
        kubernetes {
            cloud linuxcloudName
            defaultContainer 'build-environment1'
            label "linux-builds-${env.JOB_BASE_NAME}-${UUID.randomUUID().toString()}"
            slaveConnectTimeout '600000'
            yaml podDefinitionYaml
            }
    }
    environment {
        DATETIME = new Date().format("yyyyMMdd-HHmm");
        BuildVersion = "1.0.000"
        ABI_CONTAINER = "TRUE"
	BDBA_SCAN_DIR = "BDBA_SCAN"
	REPORTS_DIR = "${WORKSPACE}/abi/ubuntu_kernel/drivers/net/ethernet/intel/igc"

    }
    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '90', artifactDaysToKeepStr: '30'))
        skipDefaultCheckout()
    }
    parameters {
        booleanParam(name: 'EMAIL', defaultValue: true, description: 'Email notification upon job completion')
        booleanParam(name: 'PUBLISH', defaultValue: true, description: 'Artifacts deployment')
        string(name: 'BRANCH', trim: true, defaultValue: 'dev/intel-mainline-tracking-5.19/master', description: '''Git Branch, Tag, or CommitID identifier.
        Example: refs/heads/(branch), refs/tags/(tag), full or first 7 digits of SHA-1 hash''')
    }
    stages {
        stage('SCM') {
            steps {
                script {
                    print_node_name()

                  checkout([$class: 'GitSCM',
                userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/os.linux.kernel.mainline-tracking-staging']],
                branches: [[name: "mainline-tracking-rt/v6.5-rt5"]],
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/ubuntu_kernel'],
                [$class: 'ScmName', name: 'ubuntu_kernel'],
                [$class: 'CloneOption', timeout: 60],
                [$class: 'CleanBeforeCheckout'],
                [$class: 'CheckoutOption', timeout: 60]]])

                    checkout([$class: 'GitSCM',
                    userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/Kalla-Udaykumar/jenkins_script.git']],
                    branches: [[name: 'master']],
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/henosis'],
                    [$class: 'ScmName', name: 'henosis'],
                    [$class: 'CloneOption', timeout: 60],
                    [$class: 'CleanBeforeCheckout'],
                    [$class: 'CheckoutOption', timeout: 60]]])
                }
            }
        }
        stage('ABI') {
            steps {
               script {
                    abi.shell("cp -r ${WORKSPACE}/abi/henosis/43884/othe_build/idf ${WORKSPACE}/abi")
                    PrepareWS()
                }
            }
        }
        /*stage ('Download image from Artifactory') {
            steps {
                script {
                    def artServer = Artifactory.server "af01p-png.devtools.intel.com"
                    def artFiles  = """ {
                        "files": [
			    {
                                "pattern": "hspe-adlps-png-local/yocto/adl-ps/builds/2023/PREINT/20230411-1355/tmp-x86-2022-glibc/linux-intel-iot-lts-6.1-kernelsrc.config",
                                "target": "${WORKSPACE}/abi/ese_linux_next_next_cov/",
                                "flat": "true"
                            },
                            {
                                "pattern": "hspe-adlps-png-local/yocto/adl-ps/builds/2023/PREINT/20230411-1355/tmp-x86-musl-musl/mender-initramfs-intel-corei7-64-20230411060513.rootfs.cpio.bz2",
                                "target": "${WORKSPACE}/abi/ese_linux_next_next_cov/",
                                    "flat": "true",
                                    "recursive": "true",
                                    "sortBy": ["created"],
                                    "sortOrder": "desc",
                                    "limit": 1
                            }	
                        ]
                    }"""
                    artServer.download spec: artFiles
                }
            }
        }*/
		stage('BUILD KERNEL') {
            steps {
                script {
                    BuildInfo.instance.data["Version"] = env.BuildVersion
                    PrepareWS()
                    abi_build subComponentName: "i226-test"
                }
            }
        }

	    stage('QA: BDBA') {
            steps {
                 dir("${REPORTS_DIR}/${BDBA_SCAN_DIR}") {
                    deleteDir()
                }
                dir("${REPORTS_DIR}") {
                    zip(zipFile: "${BDBA_SCAN_DIR}/${JOB_BASE_NAME}.zip")
                }
                //Enable BDBA Scan on the final Package
                abi_scan_binary timeout: 2000, zip_file: "${REPORTS_DIR}/${BDBA_SCAN_DIR}/${JOB_BASE_NAME}.zip", wait: true, report_name: "ASL-LIN-UBUNTU", auditreport: false, custom_data: "TEAMNAME ASL-LIN-UBUNTU"
            }
        }
		/*stage('QA: COVERITY') {
            steps {
                script {
                    env.CoverityStream = "RPLS_LIN_IGC_ETH_DRIVER"
                    abi.shell("mkdir -p /OWR/Tools/coverity/2022.3.1/")
                    abi.shell("mkdir -p /OWR/Tools/cov_report/2022.3.1/")
                    abi.shell("cp -r /build/tools/engsrv/tools/coverity-analyze/. /OWR/Tools/coverity/2022.3.1/")
                    abi.shell("cp -r /build/tools/engsrv/tools/coverity-reports/. /OWR/Tools/cov_report/2022.3.1/")
                    //Enable coverity scan
                    abi_coverity_analyze server: 'https://coverityent.devtools.intel.com/prod8', stream: env.CoverityStream, version: env.BuildVersion, auditreport: false, disable_auto_copy: true
                     
                }
            }
        }

        stage("QA: VIRUS SCAN"){
            steps {
		            sh"""mkdir -p ${WORKSPACE}/upload """
                sh"""cp -r ${WORKSPACE}/abi/OWRBuild/BuildData/VirusScan/RPLS-LIN-ETH.DRIVER_McAfee.log ${WORKSPACE}/upload """
                sh"""cp -r ${WORKSPACE}/abi/OWRBuild/cov-int_${env.CoverityStream}/${env.CoverityStream}/*.pdf ${WORKSPACE}/upload """
                sh"""cp -r ${WORKSPACE}/abi/OWRBuild/cov-int_${env.CoverityStream}/output/summary.txt ${WORKSPACE}/upload """
		// Scan upload folder
                abi_scan_virus reportname: "RPLS-LIN-ETH.DRIVER", target: "${WORKSPACE}/upload", auditreport: false, disable_auto_copy: true
            }
        }
        stage("PUBLISH") {
             steps {
                script {
                        
                        //upload to artifactory
                        abi_artifact_deploy custom_file: "${WORKSPACE}/upload/",custom_deploy_path: "hspe-rpls-png-local/rpls_igc_lts_eth_driver/${UPSTREAM_DATE}/QA-Reports", custom_artifactory_url: "https://af01p-png.devtools.intel.com", cred_id: 'BuildAutomation'
                }
            }
        }*/

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
                    abi_send_email.SendEmail("${email_receipients}","${body}","${subject}")
                }
            }
        }
    }
}

// Prepare the workspace for the ingredient
void PrepareWS(String BuildConfig="idf/BuildConfig.json") {
    log.Debug("Enter")

    log.Info("This build is running on Node:${env.NODE_NAME} WorkSpace: ${env.WORKSPACE}")

    abi_setup_proxy()
    
    abi_init config: BuildConfig, ingPath: "abi", checkoutPath: "abi", skipCheckout: true

    def ctx
    ctx = abi_get_current_context()
    ctx['IngredientVersion'] = env.BuildVersion
    abi_set_current_context(ctx)

    log.Debug("Exit")
}

void set_custom_artifactpkg_name(String ArtifactPkgName) {
    log.Debug("Enter")

    def ctx
    // Define custom package name for Artifacts
    ctx = abi_get_current_context()
    custom_name = ["ArtifactPackageFile" : "", "ArtifactPackageName" : ArtifactPkgName, "Type" : "ArtifactGen"]
    ctx['Outputs'].add(custom_name)
    abi_set_current_context(ctx)

    log.Debug("Exit")
}
