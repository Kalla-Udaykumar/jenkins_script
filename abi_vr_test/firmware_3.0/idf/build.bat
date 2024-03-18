cd %WORKSPACE%\\abi
cmd /s /c "python utilities\Platform\adls\release.py %REL_TAG% -n %REL_NAME%"
echo workspace is: %WORKSPACE%
