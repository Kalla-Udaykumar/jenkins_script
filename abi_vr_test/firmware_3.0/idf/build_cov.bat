cd %WORKSPACE%\\abi
cmd /s /c "cd mini_loader-platform-cov && python SblBuild.py clean && python SblBuild.py build adls"
echo workspace is: %WORKSPACE%
