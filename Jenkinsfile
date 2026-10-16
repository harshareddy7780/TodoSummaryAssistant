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
                          --build-arg REACT_APP_API_BASE_URL=/api ^
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

        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-deploy-key',
                        keyFileVariable: 'EC2_KEY',
                        usernameVariable: 'EC2_USER'
                    )
                ]) {
                    script {
                        bat '''
                            @echo off
                            setlocal

                            set "SSH=C:\\Windows\\System32\\OpenSSH\\ssh.exe"
                            set "SCP=C:\\Windows\\System32\\OpenSSH\\scp.exe"
			    icacls "%EC2_KEY%" /inheritance:r
if errorlevel 1 exit /b 1

icacls "%EC2_KEY%" /grant:r "%USERNAME%:R"
if errorlevel 1 exit /b 1

                            if not exist "%SSH%" (
                                echo ERROR: OpenSSH ssh.exe not found.
                                exit /b 1
                            )

                            "%SSH%" -i "%EC2_KEY%" -o StrictHostKeyChecking=accept-new %EC2_USER%@13.211.84.124 "sudo docker network inspect todo-network >/dev/null 2>&1 || sudo docker network create todo-network"

                            if errorlevel 1 exit /b 1

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "sudo docker pull harshareddy7780/todo-summary-backend:%IMAGE_TAG% && sudo docker pull harshareddy7780/todo-summary-frontend:%IMAGE_TAG%"

                            if errorlevel 1 exit /b 1

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "sudo docker rm -f todo-frontend >/dev/null 2>&1 || true"

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "sudo docker rm -f todo-backend >/dev/null 2>&1 || true"

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "sudo docker run -d --name todo-backend --restart unless-stopped --env-file /home/ec2-user/app.env -p 8081:8081 --network todo-network harshareddy7780/todo-summary-backend:%IMAGE_TAG%"

                            if errorlevel 1 exit /b 1

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "sudo docker run -d --name todo-frontend --restart unless-stopped --network todo-network -p 80:8080 harshareddy7780/todo-summary-frontend:%IMAGE_TAG%"

                            if errorlevel 1 exit /b 1

                            "%SSH%" -i "%EC2_KEY%" %EC2_USER%@13.211.84.124 "for i in {1..24}; do curl -fsS http://localhost:8081/actuator/health && exit 0; sleep 5; done; exit 1"

                            if errorlevel 1 exit /b 1

                            endlocal
                        '''
                    }
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
