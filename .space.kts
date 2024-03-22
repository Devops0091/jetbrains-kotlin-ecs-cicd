job("Build and push Docker") {
    host("Run echo") {
        shellScript {
            content = """
                echo "Hello from worker!"
            """
        }
    }
        container(displayName = "docker", image = "docker:latest") {
            env["AWS_ACCESS_KEY_ID"] = "{{ project:aws_access_key_id }}"
            env["AWS_SECRET_ACCESS_KEY"] = "{{ project:aws_secret_access_key }}"
            env["AWS_REGION"] = "{{ project:aws_region }}"
            shellScript {
                content = """
                    # Configure AWS CLI
                    apk --no-cache add \
                      curl \
                      python3 \
                      py3-pip \
                      groff \
                      less \
                      && python3 -m venv /usr/share/virtualenv \
                      && source /usr/share/virtualenv/bin/activate \
                      && pip install --upgrade pip \
                      && pip install awscli \
                      && deactivate \
                      && ln -s /usr/share/virtualenv/bin/aws /usr/local/bin/aws
    
                    aws configure set aws_access_key_id ${'$'}AWS_ACCESS_KEY_ID
                    aws configure set aws_secret_access_key ${'$'}AWS_SECRET_ACCESS_KEY
                    aws configure set default.region ${'$'}AWS_REGION
    
                    # Get the latest commit ID
                    COMMIT_ID=$(git rev-parse --short HEAD)
                    # Build the Docker image with commit ID as tag
                    docker build -t dev-ecr-example-backend:${'$'}COMMIT_ID .
                    # Authenticate Docker to AWS ECR
                    aws ecr get-login-password --region me-south-1 | docker login --username AWS --password-stdin 649690477760.dkr.ecr.me-south-1.amazonaws.com/dev-ecr-example-backend
                    # Tag the local Docker image
                    docker tag dev-ecr-example-backend:${'$'}COMMIT_ID 649690477760.dkr.ecr.me-south-1.amazonaws.com/dev-ecr-example-backend:${'$'}COMMIT_ID
                    # Push the Docker image to AWS ECR
                    docker push 649690477760.dkr.ecr.me-south-1.amazonaws.com/dev-ecr-example-backend:${'$'}COMMIT_ID
                """
            }
        }

        container(displayName = "aws-cli", image = "docker:latest") {

            env["AWS_ACCESS_KEY_ID"] = "{{ project:aws_access_key_id }}"
            env["AWS_SECRET_ACCESS_KEY"] = "{{ project:aws_secret_access_key }}"
            env["AWS_REGION"] = "{{ project:aws_region }}"
            env["TASK_FAMILY"] = "{{ project:task_family }}"
            env["ECS_CLUSTER"] = "{{ project:ecs_cluster }}"
            env["SERVICE_NAME"] = "{{ project:service_name }}"

            shellScript {
                content = """
                    # Configure AWS CLI
                    apk --no-cache add \
                      curl \
                      python3 \
                      py3-pip \
                      groff \
                      less \
                      && python3 -m venv /usr/share/virtualenv \
                      && source /usr/share/virtualenv/bin/activate \
                      && pip install --upgrade pip \
                      && pip install awscli \
                      && deactivate \
                      && ln -s /usr/share/virtualenv/bin/aws /usr/local/bin/aws

                    aws configure set aws_access_key_id ${'$'}AWS_ACCESS_KEY_ID
                    aws configure set aws_secret_access_key ${'$'}AWS_SECRET_ACCESS_KEY
                    aws configure set default.region ${'$'}AWS_REGION
                    
                    # Install jq for JSON parsing
                    apk --no-cache add jq

                    # Get the latest commit ID
                    COMMIT_ID=$(git rev-parse --short HEAD)

                    FULL_IMAGE=649690477760.dkr.ecr.me-south-1.amazonaws.com/dev-ecr-example-backend:${'$'}COMMIT_ID


                    TASK_DEFINITION=$(aws ecs describe-task-definition --task-definition ${'$'}TASK_FAMILY --region ${'$'}AWS_REGION)
                    NEW_TASK_DEFINITION=$(echo ${'$'}TASK_DEFINITION | jq --arg IMAGE "${'$'}FULL_IMAGE" '.taskDefinition | .containerDefinitions[0].image = ${'$'}IMAGE | del(.taskDefinitionArn) | del(.revision) | del(.status) | del(.requiresAttributes) | del(.compatibilities) |  del(.registeredAt)  | del(.registeredBy)')
                    NEW_TASK_INFO=$(aws ecs register-task-definition --region ${'$'}AWS_REGION --cli-input-json "${'$'}NEW_TASK_DEFINITION")
                    NEW_REVISION=$(echo ${'$'}NEW_TASK_INFO | jq '.taskDefinition.revision')
                   aws ecs update-service --cluster ${'$'}ECS_CLUSTER --service ${'$'}SERVICE_NAME --task-definition ${'$'}TASK_FAMILY:${'$'}NEW_REVISION
                """
            }
        }    
}
