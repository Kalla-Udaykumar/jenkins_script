## COVERITY COMPILATION ISSUE

* Error:
```
16:33:36  Error: intermediate directory contains no translation units.
16:33:36  Coverity Defect Commit Client version 2023.3.4 on Linux 4.12.14-122.60-default x86_64
16:33:36  Internal version numbers: 858be4c112 p-2023.3-push-67
16:33:36  
16:33:42  [ERROR] Analysis results are missing.
16:33:42          Please read the documentation to determine the appropriate
16:33:42          ordering in which to run the Coverity Prevent commands.
```

## URL Link's

- [Jenkins_Test Job](https://cbjenkins-pg.devtools.intel.com/teams-iotgdevops01/job/iotgdevops01/job/NON_PROD/job/UKALLA/job/EHL-FIRMWARE-PSE.ZEPHYR.RTOS-CI-PCT/)
- [Git hub coverity script](https://github.com/intel-sandbox/ukallax_non_prod/blob/master/EHL-FIRMWARE-PSE.ZEPHYR.RTOS-CI-PCT/Jenkinsfile_coverity.ci)
- [Git hub Klocwork script](https://github.com/intel-sandbox/ukallax_non_prod/blob/master/EHL-FIRMWARE-PSE.ZEPHYR.RTOS-CI-PCT/Jenkinsfile.klocwork)
- [Git PR to trigger CI job](https://github.com/intel-innersource/os.rtos.zephyr.iot.zephyr/pull/267)


## Note

- `if you don't have access to PR let me know so that I can commit the PR and trigger Jenkins Job`
- `This Jenkins job won't trigger manually by build with parameters` 
