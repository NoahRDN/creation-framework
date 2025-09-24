@echo off
setlocal enabledelayedexpansion

:: -------------------------
:: Configurations du projet
:: -------------------------

cd /d "%~dp0"

:: Charger .env
for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "%%a=%%b"
)

:: Vérification
echo Nom du projet: %project_name%
echo Mode de compilation: %mode%
echo Dossier de travail: %work_dir%
echo Type de sortie: %OUTPUT_TYPE%
echo Chemin de sortie JAR: %jar_output_path%
echo Dossier de build: %build%

pause

:: -------------------------
:: Nettoyage
:: -------------------------
if exist "%build%" rmdir /s /q "%build%"
mkdir "%build%\WEB-INF\classes"
mkdir "%build%\WEB-INF\lib"


:: -------------------------
:: Compilation Java (uniquement si mode = dev)
:: -------------------------
if /i "%mode%"=="dev" (
    echo Compilation des fichiers Java...
    dir /s /b "%src%\*.java" > "%build%\sources.txt"

    for %%x in ("%build%\sources.txt") do if %%~zx==0 (
        echo ERREUR : Aucun fichier Java trouvé dans %src%
        del "%build%\sources.txt"
        pause
        exit /b 1
    )

    javac -cp "%lib%\*" -d "%build%\WEB-INF\classes" @"%build%\sources.txt"
    del "%build%\sources.txt"
) else (
    echo Mode test actif, compilation Java ignorée.
)


:: -------------------------
:: Copie des librairies
:: -------------------------
xcopy "%lib%\*" "%build%\WEB-INF\lib\" /Y


:: -------------------------
:: WAR
:: -------------------------
if /i "%OUTPUT_TYPE%"=="war" (
    :: Vérifie que %web% est défini
    if not defined web (
        echo ERREUR : La variable %%web%% n'est pas définie dans .env
        pause
        exit /b 1
    )


    if not exist "%web%" (
        echo ERREUR : Le dossier web "%web%" est introuvable
        pause
        exit /b 1
    )


    @REM :: Copie les fichiers web (hors WEB-INF)
    @REM echo Copie des fichiers web (hors WEB-INF)...
    @REM if exist exclude-webinf.txt (
    @REM     xcopy "%web%\*" "%build%\" /E /I /Y /EXCLUDE:exclude-webinf.txt
    @REM ) else (
    @REM     xcopy "%web%\*" "%build%\" /E /I /Y
    @REM )

    :: Copie web.xml dans WEB-INF
    if exist "%web%\WEB-INF\web.xml" (
        echo Copie de web.xml...
        xcopy "%web%\WEB-INF\web.xml" "%build%\WEB-INF\" /Y
    ) else (
        echo ATTENTION : web.xml introuvable dans %web%\WEB-INF\
    )

    :: Création du WAR
    echo Création du WAR...
    jar -cvf "%work_dir%\%project_name%.war" -C "%build%" .

    :: Déploiement vers Tomcat
    echo Déploiement vers Tomcat...
    copy /Y "%work_dir%\%project_name%.war" "%tomcat_webapps%"
    echo Projet déployé avec succès.

)


:: -------------------------
:: JAR
:: -------------------------
if /i "%OUTPUT_TYPE%"=="jar" (
    echo Création du JAR...
    jar -cvf "%jar_output_path%\%project_name%.jar" -C "%build%\WEB-INF\classes" .
    echo JAR généré avec succès.
)

pause
