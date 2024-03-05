#!groovy
@Library('abi') _

import owm.common.BuildInfo
Map buildinfo = BuildInfo.instance.data

linuxcloudName = 'gar-png-nswe-sws-ada'

def podDefinitionYaml = """
kind: Pod
apiVersion: v1
spec:
  volumes:
    - name: nfs
      persistentVolumeClaim:
        claimName: png-nfs
    - name: github-config
      secret:
        secretName: gitconfig
    - name: sshkeys
      secret:
        secretName: ssh-keys
    - name: netrc-lab
      secret:
        secretName: netrc
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
    image: amr-registry.caas.intel.com/esc-devops/plat/lin/mips-abi/ubuntu2004:05032024_0749
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
        DAY = new Date().format("u")
        DATETIME = new Date().format("yyyyMMdd-HHmm")
        BUILD_YEAR = new Date().format("yyyy")
        BuildVersion = "1.0.000"
        ABI_CONTAINER = "TRUE"
        BDBA_SCAN_DIR = "BDBA_SCAN"
        REPORTS_DIR = "${WORKSPACE}/upload"
        DC_TEAMNAME = "GVP-LIN-MIPS"
        DATETIME = new Date().format("yyyyMMdd-HHmm");
        ARTIFACTORY_REPO = "hspe-mips-png-local/daily/linux/build"
        ARTIFACTORY_SERVER = "af01p-png.devtools.intel.com"
        DOCKER_IMG = "amr-registry.caas.intel.com/esc-apps/gio/ubuntu18.04:20210927_1110"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '90', artifactDaysToKeepStr: '30'))
        skipDefaultCheckout()
    }

    parameters {
        booleanParam(name: 'EMAIL', defaultValue: true, description: 'Email notification upon job completion')
        booleanParam(name: 'PUBLISH', defaultValue: true, description: 'Artifacts deployment')
        string(name: 'BRANCH_NAME', defaultValue: 'dev-stable/iti-proto', description: 'Branch for config file')
    }

    stages {
        stage('SCM') {
            steps {
                parallel (
                    "MIPS_REPO": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.iot.tools.rpbs.mips.git']],
                        branches: [[name: "master"]],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/mips'],
                        [$class: 'ScmName', name: 'mips'],
                        [$class: 'CleanBeforeCheckout']]])
                    },
                    "GVP_REPO": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.services.gvp.observability.git']],
                        branches: [[name: "${BRANCH_NAME}"]],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/applications.services.gvp.observability'],
                        [$class: 'ScmName', name: 'applications.services.gvp.observability'],
                        [$class: 'CleanBeforeCheckout']]])
                    },
                    "esc-services": {
                        checkout changelog: false, scm: ([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.henosis.build.automation.services.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/esc-engservices'],
                        [$class: 'CleanBeforeCheckout']]])
                    },
                    "henosis-repo": {
                        checkout changelog: false, scm: ([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/Kalla-Udaykumar/jenkins_script.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/henosis'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    }

                )
            }
        }

        stage('ABI') {
            steps {
               script {
                    abi.shell("cp -r ${WORKSPACE}/abi/henosis/jira-44015/idf ${WORKSPACE}/abi")
                    PrepareWS()
                }
            }
        }

        stage('BUILD') {
            steps {
                script {
                    BuildInfo.instance.data["Version"] = env.BuildVersion
                    PrepareWS()
                    getOS()
                    abi_build subComponentName: "build"
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
                abi_scan_binary timeout: 2000, zip_file: "${REPORTS_DIR}/${BDBA_SCAN_DIR}/${JOB_BASE_NAME}.zip", wait: true, report_name: "MIPS-OBSERVABILITY", auditreport: false, disable_auto_copy: true
            }
        }

        stage("QA-VIRUS SCAN"){
             steps {
                script {
                    abi_scan_virus reportname: "MIPS-OBSERVABILITY", target: "${WORKSPACE}/upload", auditreport: false
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
