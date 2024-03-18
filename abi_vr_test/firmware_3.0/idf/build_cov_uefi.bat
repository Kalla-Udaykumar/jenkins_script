cd %WORKSPACE%\\abi
cmd /s /c "cd mini_loader-uefi-cov && python BuildPayload.py clean && python BuildPayload.py build adls"
echo workspace is: %WORKSPACE%
