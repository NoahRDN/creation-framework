@echo off
setlocal enabledelayedexpansion

:: -------------------------
:: Configurations du projet
:: -------------------------
set "project_name=SpringJakarta"
set "work_dir=H:\itu\s5\Framework\Framework"
set "build=%work_dir%\build"
set "src=%work_dir%\src\main\java"
set "web=%work_dir%\src\main\webapp"
set "lib=%work_dir%\lib"
set "tomcat_webapps=H:\itu\apache-tomcat-10.1.28\webapps"

:: -------------------------
:: Nettoyage
:: -------------------------
if exist "%build%" rmdir /s /q "%build%"
mkdir "%build%\WEB-INF\classes"
mkdir "%build%\WEB-INF\lib"

:: -------------------------
:: Compilation Java avec Jakarta Servlet
:: -------------------------
:: On crée un fichier sources.txt avec tous les fichiers Java
dir /s /b "%src%\*.java" > "%build%\sources.txt"

:: Compilation en incluant tous les JAR dans lib (notamment jakarta.servlet-api)
javac -cp "%lib%\*" -d "%build%\WEB-INF\classes" @"%build%\sources.txt"

:: Nettoyage du fichier temporaire
del "%build%\sources.txt"

:: -------------------------
:: Copie des fichiers web et configuration
:: -------------------------
xcopy "%web%\*" "%build%\" /E /I /Y
xcopy "%lib%\*" "%build%\WEB-INF\lib\" /Y

:: -------------------------
:: Création du fichier WAR
:: -------------------------
cd /d "%build%"
jar -cvf "%work_dir%\%project_name%.war" *

:: -------------------------
:: Déploiement vers Tomcat
:: -------------------------
copy "%work_dir%\%project_name%.war" "%tomcat_webapps%"
echo Projet déployé avec succès dans %tomcat_webapps%\%project_name%.war

pause
