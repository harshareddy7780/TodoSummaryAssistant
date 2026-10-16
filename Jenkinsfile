pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        DOCKERHUB_USERNAME = 'harshareddy7780'
        BACKEND_IMAGE = 'harshareddy7780/todo-summary-backend'
        FRONTEND_IMAGE = 'harshareddy7780/todo-summary-frontend'
        DOCKER = 'C:\\Users\\143ha\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker.exe'
        COMPOSE = 'C:\\Users\\143ha\\AppData\\Local\\Programs\\DockerDesktop\\resources\\bin\\docker-compose.exe'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

    stage('Validate Configuration') {
            steps {
                bat '''
                        set "MYSQL_PASSWORD=ci-validation-placeholder"
                        set "MYSQL_ROOT_PASSWORD=ci-validation-placeholder"
                        set "GRAFANA_ADMIN_PASSWORD=ci-validation-placeholder"
                        "%COMPOSE%" config > nul
if errorlevel 1 exit /b 1
                '''
            }
    }


        stage('Test Backend') {
            steps {
                bat 'call Backend\\todo-summary-assistant\\mvnw.cmd -f Backend\\todo-summary-assistant\\pom.xml test'
            }
        }

	
	stage('Test Frontend') {
    		environment {
        		PATH = "C:\\Users\\143ha;${env.PATH}"
    		}
    		steps {
        		dir('Frontend/todo') {
            			bat 'npm.cmd ci'
            			bat 'npm.cmd test -- --watchAll=false --passWithNoTests'
        		}
   		 }
	}

        stage('Build Docker Images') {
            steps {
                script {
                    env.IMAGE_TAG = bat(
                        script: '@git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim().readLines().last().trim()

                    bat '''
                        "%DOCKER%" build ^
                          -t "%BACKEND_IMAGE%:%IMAGE_TAG%" ^
                          -t "%BACKEND_IMAGE%:latest" ^
                          Backend\\todo-summary-assistant

                        if errorlevel 1 exit /b 1

                        "%DOCKER%" build ^
                          --build-arg REACT_APP_API_BASE_URL=http://localhost:8081/api ^
                          -t "%FRONTEND_IMAGE%:%IMAGE_TAG%" ^
                          -t "%FRONTEND_IMAGE%:latest" ^
                          Frontend\\todo

                        if errorlevel 1 exit /b 1
                    '''
                }
            }
        }


        stage('Push Images to Docker Hub') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DH_USER',
                        passwordVariable: 'DH_TOKEN'
                    )
                ]) {
                    bat '''
                        @echo off
                        setlocal
                        set "DOCKER_CONFIG=%WORKSPACE%\\.docker-ci"
                        if not exist "%DOCKER_CONFIG%" mkdir "%DOCKER_CONFIG%"

                        echo Logging in to Docker Hub...
                        powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; $env:DH_TOKEN | & $env:DOCKER login --username $env:DH_USER --password-stdin; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }"

                        if errorlevel 1 exit /b 1

                        "%DOCKER%" push "%BACKEND_IMAGE%:%IMAGE_TAG%"
                        if errorlevel 1 exit /b 1

                        "%DOCKER%" push "%FRONTEND_IMAGE%:%IMAGE_TAG%"
                        if errorlevel 1 exit /b 1

                        "%DOCKER%" push "%BACKEND_IMAGE%:latest"
                        if errorlevel 1 exit /b 1

                        "%DOCKER%" push "%FRONTEND_IMAGE%:latest"
                        if errorlevel 1 exit /b 1

                        "%DOCKER%" logout
                        if errorlevel 1 exit /b 1

                        rmdir /s /q "%DOCKER_CONFIG%"
                        endlocal
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: Tests passed and Docker images were built.'
        }
        failure {
            echo 'FAILURE: Check the Jenkins Console Output.'
        }
        always {
            echo 'Pipeline execution finished.'
        }
    }
}
