@echo off
if "%~1"=="" (
  echo Usage: package.bat folder_name
  exit /b 1
)

cd src

set folder_name=%~1
set zip_file=%folder_name%.zip

echo Creating ZIP archive %zip_file% from folder %folder_name%...
7z a -tzip %zip_file% "%folder_name%"

if errorlevel 1 (
  echo An error occurred while creating the ZIP archive.
  cd ..
  exit /b 1
)

echo ZIP archive %zip_file% created successfully.
move %zip_file% ..
cd ..
exit /b 0
