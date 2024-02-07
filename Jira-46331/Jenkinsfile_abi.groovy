#!groovy
@Library('abi') _

import owm.common.BuildInfo
Map buildinfo = BuildInfo.instance.data

email_receipients = '$DEFAULT_RECIPIENTS,'
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
    image: amr-registry.caas.intel.com/esc-devops/plat/lin/gvp/obs/abi/ubuntu2004:20240207_1049
    resources:
      requests:
        cpu: "4.0"
        memory: "4Gi"
      limits:
        cpu: "5.0"
        memory: "5Gi"
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
            REPORTS_DIR = "${WORKSPACE}/abi/upload"
            DATETIME = new Date().format("yyyyMMdd-HHmm");
            BuildVersion = "1.0.000"
            ABI_CONTAINER = false
            GIT_URL = "https://github.com/intel-innersource/libraries.security.services.psd-application.git"
        }
        options {
            timestamps()
            disableConcurrentBuilds()
            buildDiscarder(logRotator(numToKeepStr: '90', artifactDaysToKeepStr: '30'))
            skipDefaultCheckout()
        }
        parameters {
            booleanParam(name: 'EMAIL', defaultValue: true, description: 'Email notification upon job completion')
            booleanParam(name: 'PUBLISH', defaultValue: false, description: 'Publish the artifacts')
        }
        stages {
            stage('SCM') {
                steps {
                    script {
                        print_node_name()
                             parallel(
                    "gvp_obs-cov": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.services.gvp.observability']],
                        branches: [[name: 'dev-stable/iti-proto']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir:'abi/gvp_obs_cov'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    },
                    "Protex-gvp_obs_sw": {
                        checkout([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/applications.services.gvp.observability']],
                        branches: [[name: 'dev-stable/iti-proto']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir:'abi/protex-gvp_obs_sw'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    },
                    "esc-engservices": {
                        checkout changelog: false, scm: ([$class: 'GitSCM',
                        userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.henosis.build.automation.services.git']],
                        branches: [[name: 'refs/heads/master']],
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/esc-engservices'],
                        [$class: 'CloneOption', timeout: 60],
                        [$class: 'CleanBeforeCheckout'],
                        [$class: 'CheckoutOption', timeout: 60]]])
                    },
                    "henosis": {
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
                }   
                stage('ABI') {
                    steps {
                        script {
                        abi.shell("cp -r ${WORKSPACE}/abi/henosis/Jira-46331/idf ${WORKSPACE}/abi")
                        PrepareWS()
                    }
                }
            }
            stage('QA: COVERITY') {
                steps {
                    script {
                        env.CoverityStream = "GEN_LIN_GVP_QA_DLY"
                        abi.shell("mkdir -p /OWR/Tools/coverity/2022.3.1/")
                        abi.shell("mkdir -p /OWR/Tools/cov_report/2022.3.1/")
                        abi.shell("cp -r /build/tools/engsrv/tools/coverity-analyze/. /OWR/Tools/coverity/2022.3.1/")
                        abi.shell("cp -r /build/tools/engsrv/tools/coverity-reports/. /OWR/Tools/cov_report/2022.3.1/")
                        //Enable coverity scan
                        abi_coverity_analyze server: 'https://coverityent.devtools.intel.com/prod5', stream: env.CoverityStream, version: env.BuildVersion, auditreport: true                       
                    }
                }
            }  
            stage('QA: PROTEX') {
                steps {
                    dir("${WORKSPACE}"){
                        script {
                            abi.shell("cp -r /build/tools/engsrv/tools/protexIP/* /OWR/Tools/protexIP-7.8.4")
                            abi.shell("cp -r ${WORKSPACE}/abi/henosis/cac/gen/lin/psd/idf ${WORKSPACE}/abi/hwrotdiscovery")
                    
                            def ctx
                            ctx = abi_get_current_context()
                            ctx['IngredientPath'] = "abi/protex-gvp_obs_sw"
                            abi_set_current_context(ctx)
                            //abi_scan_ip ignore: "${WORKSPACE}/abi/protex-gvp_obs_sw" 
                                                    
                                }
                            }
                        }
                          
                    /*stage("QA: VIRUS SCAN"){
                        steps {
                          script {
                                sh """ 
                                    cp -R ${WORKSPACE}/abi/hwrotdiscovery/OWRBuild/protex/*.xlsx ${WORKSPACE}/abi/upload/
                                    cp -r ${WORKSPACE}/abi/OWRBuild/cov-int_${env.CoverityStream}/${env.CoverityStream}/*.pdf ${WORKSPACE}/abi/upload 
                                    cp -r ${WORKSPACE}/abi/OWRBuild/cov-int_${env.CoverityStream}/output/summary.txt ${WORKSPACE}/abi/upload """
                                    //enable virus scan with ABI
                                    abi_scan_virus reportname: "GEN-LIN-PSD", target: "${WORKSPACE}/abi/upload", auditreport: true
                                    sh """ cp -R ${WORKSPACE}/abi/hwrotdiscovery/OWRBin/Documents/VirusScan ${WORKSPACE}/abi/upload/ """ 
                                }   
                           } 
                       }
                    stage("PUBLISH") {
                        steps {
                            script {
                                //upload to artifactory
                                abi_artifact_deploy custom_file: "${REPORTS_DIR}",custom_deploy_path: "pse-tgl-local/psd/${DATETIME}/QA-Reports", custom_artifactory_url: "https://ubit-artifactory-ba.intel.com", additional_props: "retention.days=365", cred_id: 'BuildAutomation'
                            }
                        }
                    }*/
                }           
            /*post {
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
        }*/
    }
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
