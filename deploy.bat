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
echo Dossier de travail: %work_dir%
echo Chemin de sortie JAR: %jar_output_path%
echo ==========================

pause

:: -------------------------
:: JAR
:: -------------------------

:: If a Maven project exists, build it and copy dependencies first
if exist "%work_dir%\pom.xml" (
    echo Maven project detected in %work_dir% - running build and copying dependencies
    pushd "%work_dir%"
    echo Running: mvn clean package
    call mvn clean package
    if errorlevel 1 (
        echo ERREUR: mvn clean package a echoue
        popd
        rem continue - we'll try to find an existing jar in target or fall back
    ) else (
        echo mvn clean package termine avec succes
    )

    echo Running: mvn dependency:copy-dependencies -DoutputDirectory=target\dependency
    call mvn dependency:copy-dependencies -DoutputDirectory=target\dependency
    if errorlevel 1 (
        echo ATTENTION: mvn dependency:copy-dependencies a echoue
    ) else (
        echo Dependencies copied to target\dependency
    )

    popd
) else (
    echo Aucun pom.xml trouve dans %work_dir% - saut des etapes Maven
)

echo Recherche du JAR Maven dans "%work_dir%\target"...
set "JAR_FOUND="
if exist "%work_dir%\target\*.jar" (
    rem Trouver le JAR le plus récent (dir /b /a:-d retourne par date décroissante)
    for /f "delims=" %%F in ('dir /b /a:-d "%work_dir%\target\*.jar" 2^>nul') do (
        rem %%F is the file name; construct full path explicitly to avoid expansion issues
        set "JAR_NAME=%%F"
        set "JAR_FOUND=%work_dir%\target\%%F"
        goto :afterFindJar
    )
)

:afterFindJar
if defined JAR_FOUND (
    echo JAR Maven trouvé : %JAR_FOUND%
    if not exist "%jar_output_path%" mkdir "%jar_output_path%"
    if not exist "%JAR_FOUND%" (
        echo ERREUR: le fichier JAR trouve n'existe pas : %JAR_FOUND%
        goto :endJarSection
    )
    echo Copie du JAR vers %jar_output_path%...
    echo Copie depuis : "%JAR_FOUND%"
    echo Vers : "%jar_output_path%\"
    copy /Y "%JAR_FOUND%" "%jar_output_path%\"
    echo JAR copié.
    rem --- Copier les dépendances vers %jar_output_path%
    set "DEPS_COPIED=0"
    for %%D in ("%work_dir%\target\dependency") do (
        if exist "%%~D" (
            echo Copie des dépendances depuis: %%~D
            if not exist "%jar_output_path%" mkdir "%jar_output_path%"
            xcopy "%%~D\*" "%jar_output_path%" /E /I /Y
            set "DEPS_COPIED=1"
        )
    )
    if "!DEPS_COPIED!"=="0" echo Aucune dépendance trouvee dans les dossiers connus.
    echo Operation terminee.
    goto :endJarSection
)

pause
