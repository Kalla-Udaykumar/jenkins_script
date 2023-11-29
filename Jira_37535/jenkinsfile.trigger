#!groovy
@Library('abi@2.6.0') _

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
    image: amr-registry.caas.intel.com/esc-devops/dev/rtoh/abi/ub20/tsn:test
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

def buildJob( BRANCH_NAME, PLATFORM ){
    return {
	println ("platform" + "${PLATFORM}")
        //Get information for downstream job trigger
        getConfig(PLATFORM)

        println ("Trigger TSN AUTOMATION - ${env.BRANCH_NAME} - ${PLATFORM}")
        build ( job: "iotgdevops01/GEN-LIN-TSN.KERNEL-BD-DLY-BUILD", 
        parameters: [
		string(name: 'PLATFORM', value: "${PLATFORM}"),
		string(name: 'BRANCH_NAME', value: "${env.BRANCH_NAME}"),
		string(name: 'CONFIG_BRANCH', value: "${CONFIG_BRANCH}"),
		string(name: 'OneBKC_url', value: "${OneBKC_url}"),
		string(name: 'OneBKC_branch', value: "${OneBKC_branch}"),
		string(name: 'OneBKC_file', value:"${OneBKC_file}"),
		string(name: 'OneBKC_ifwi', value:"${OneBKC_ifwi}"),
		string(name: 'release_git_url', value:"${release_git_url}"),
		string(name: 'release_rt_branch', value:"${release_rt_branch}"),
		string(name: 'release_lts_branch', value:"${release_lts_branch}"),
		string(name: 'kernel_rt', value:"${kernel_rt}"),
		string(name: 'kernel_lts', value:"${kernel_lts}"),
		string(name: 'kernelsrc_lts_tar', value:"${kernelsrc_lts_tar}"),
		string(name: 'kernelsrc_lts_cfg', value:"${kernelsrc_lts_cfg}"),
		string(name: 'kernelsrc_rt_tar', value:"${kernelsrc_rt_tar}"),
		string(name: 'kernelsrc_rt_cfg', value:"${kernelsrc_rt_cfg}"),
		string(name: 'mender_file', value:"${mender_file}"),
		string(name: 'email', value:"${email}"),
		string(name: 'gio_cmd', value:"${gio_cmd}"),
		
	], wait: true, propagate: false)
	
    }
}

pipeline {
    triggers { 
        cron('H 18 * * *')
    }
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
        ARTIFACTORY_SERVER = "af01p-png.devtools.intel.com"
        ARTIFACTORY_REPO = "hspe-tsn_automation-png-local/ehl/lin"
        upload_folder = "daily"
        DOCKER_IMG = "amr-registry.caas.intel.com/esc-apps/gio/ubuntu18.04:20210927_1110"
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
        string(name: 'CONFIG_BRANCH', defaultValue: 'master', description: 'Branch for config file')
    }
    stages {
	stage("Henosis-Repo") {
		steps {
			script {
			
				checkout([$class: 'GitSCM',
				userRemoteConfigs: [[credentialsId: 'GitHub-Token', url: 'https://github.com/intel-innersource/libraries.devops.jenkins.cac.git']],
				branches: [[name: "${CONFIG_BRANCH}"]],
				extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'abi/henosis'],
				[$class: 'ScmName', name: 'henosis'],
				[$class: 'CloneOption', timeout: 60],
				[$class: 'CleanBeforeCheckout'],
				[$class: 'CheckoutOption', timeout: 60]]])
			    
				props = readYaml file: 'abi/henosis/cac/gen/lin/tsn/kernel/daily/config.yml'
				println("${props.daily.branch.keySet()}")
				if (env.BRANCH_NAME in props.daily.branch.keySet()) {
				    println("Build continue")
				} else {
				    println("Build stop")
				    currentBuild.result = 'ABORTED'
				    error('Aborting build. Branch Name not in config.yml file.')
				}
				
				def platform_list = props.daily.branch."${env.BRANCH_NAME}".keySet()
			}
		}
	}	
        stage ('BUILD') {
            steps {
                script {
		
			props = readYaml file: 'abi/henosis/cac/gen/lin/tsn/kernel/daily/config.yml'
			platform_list = props.daily.branch."${env.BRANCH_NAME}".keySet()
			parallelBuilds = [:]

			for (PLATFORM in platform_list) {
				parallelBuilds["Trigger TSN AUTOMATION - ${env.BRANCH_NAME} - ${PLATFORM}"] = this.buildJob( BRANCH_NAME , PLATFORM )
			}
			parallel parallelBuilds
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
                
                // Trigger email
                if (params.EMAIL == true) {
                    emailext body: '${SCRIPT, template="managed:pipeline.html"}', subject: '$DEFAULT_SUBJECT', replyTo: '$DEFAULT_REPLYTO', to: '$DEFAULT_RECIPIENTS, iotg.ped.sw.ese.ethernet-tsn@intel.com' 
                }


            }
        }
    }
}

// Get information from config.yml file
def getConfig(PLATFORM) {
        props = readYaml file: 'abi/henosis/cac/gen/lin/tsn/kernel/daily/config.yml'
        print(props)
        //OneBKC variable
        env.OneBKC_url = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".OneBKC_url}"
        env.OneBKC_branch = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".OneBKC_branch}"
        env.OneBKC_file = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".OneBKC_file}"
        env.OneBKC_ifwi = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".OneBKC_ifwi}"
        env.release_git_url = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".release_git_url}"
        env.release_rt_branch = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".release_rt_branch}"
        env.release_lts_branch = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".release_lts_branch}"
        env.kernel_rt = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".kernel_rt}"
        env.kernel_lts = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".kernel_lts}"
        env.kernelsrc_lts_tar = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".kernelsrc_lts_tar}"
        env.kernelsrc_lts_cfg = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}". kernelsrc_lts_cfg}"
        env.kernelsrc_rt_tar = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".kernelsrc_rt_tar}"
        env.kernelsrc_rt_cfg = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".kernelsrc_rt_cfg}"
        env.mender_file = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".mender_file}"
	email = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".email}"

        println("${env.OneBKC_url}")
        println("${env.OneBKC_branch}")
        println("${env.OneBKC_file}")
        println("${env.OneBKC_ifwi}")
        println("${env.release_git_url}")
        println("${env.release_rt_branch}")
        println("${env.release_lts_branch}")
        println("${env.kernel_rt}")
        println("${env.kernel_lts}")
        println("${env.kernelsrc_lts_tar}")
        println("${env.kernelsrc_lts_cfg}")
        println("${env.kernelsrc_rt_tar}")
        println("${env.kernelsrc_rt_cfg}")
        println("${env.mender_file}")

        switch("${DAY}") {
            case "1":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Mon}"
                break
            case "2":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Tue}"
                break
            case "3":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Wed}"
                break
            case "4":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Thu}"
                break
            case "5":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Fri}"
                break
            case "6":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Sat}"
                break
            case "7":
                gio_cmd = "${props.daily.branch."${env.BRANCH_NAME}"."${PLATFORM}".gio_cmd.Sun}"
                break
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
