# TSN Daily UBUNTU Build Documentation

## Summary
The TSN Daily Yocto Build is responsible for building multiple versions of the Linux kernel on different platforms such as ADLS, RPLS, TGLU.

## Jenkins Jobs

### 1. Trigger Job
**Job Name:** GEN-LIN-TSN.KERNEL-BD-DLY-TRIGGER

**Purpose:** This job is responsible for triggering the daily Yocto builds.

**Jenkins Link:** [Trigger Job](https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/job/iotgdevops01/job/GEN-LIN-TSN.KERNEL-BD-DLY-TRIGGER/)

**Jenkinsfile:** Jenkinsfile.trigger

### 2. Build Job
**Job Name:** GEN-LIN-TSN.KERNEL-BD-DLY-BUILD

**Purpose:** This job handles the actual building of the Linux kernel for the specified platforms.

**Jenkins Link:** [Build Job](https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/job/iotgdevops01/job/GEN-LIN-TSN.KERNEL-BD-DLY-BUILD/)

**Jenkinsfile:** Jenkinsfile_abi.build
