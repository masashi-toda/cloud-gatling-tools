# Makefile for gatling tools
.DEFAULT_GOAL := help

# Load env file
ENV ?= local
env ?= .env.$(ENV)
include $(env)
export $(shell sed 's/=.*//' $(env))

# -----------------------------------------------------------------
#    ENV VARIABLE
# -----------------------------------------------------------------
NAME = cloud-gatling-tools

# Tools version
TOOLS_DIR = ./tools

# SBT settings
SBT_VERSION = 1.6.2
SBT_CMD     = $(TOOLS_DIR)/sbt/bin/sbt -mem 2048 #-verbose -debug

# Terraform settings
TFM_VARS = -var 'prefix=$(NAME)' \
           -var 'profile=$(AWS_PROFILE)' \
           -var 'region=$(AWS_REGION)' \
           -var 'gatling_s3_log_bucket_ip_list=$(AWS_BUCKET_IP_LIST)'

# -----------------------------------------------------------------
#    Main targets
# -----------------------------------------------------------------

.PHONY: env
env: ## Print useful environment variables to stdout
	@echo NAME         = $(NAME)
	@echo API_BASE_URL = $(GATLING_TARGET_ENDPOINT_BASE_URL)
	@echo $(env)
	@echo $(TFM_VARS)

.PHONY: help
help: env
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' Makefile | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

# -----------------------------------------------------------------
#    Sbt targets
# -----------------------------------------------------------------

.PHONY: clean
clean: ## Remove temporary files
	@rm -rf target
	@rm -rf */target
	@$(SBT_CMD) clean

.PHONY: format
format: ## Code formatter for Scala
	@$(SBT_CMD) scalafmtAll

.PHONY: compile
compile: ## Compile scala code
	@$(SBT_CMD) compile

.PHONY: package
package: ## Package scala project
	@$(SBT_CMD) package

# -----------------------------------------------------------------
#    Gatling targets
# -----------------------------------------------------------------

.PHONY: run-local-gatling-test
run-local-simulator: ## Run local gatling test simulation
	@$(SBT_CMD) gatling-test/GatlingIt/test

.PHONY: run-cloud-gatling-test
run-cloud-gatling-test: ## Run cloud gatling test simulation
	@$(SBT_CMD) gatling-aggregate-runner/gatling/runTask

# -----------------------------------------------------------------
#    Terraform targets
# -----------------------------------------------------------------

.PHONY: terraform-init
terraform-init: ## Init terraform
	@cd terraform && terraform init $(TFM_VARS) && cd ..

.PHONY: terraform-apply
terraform-apply: ## Apply terraform
	@cd terraform && terraform apply $(TFM_VARS) && cd ..

.PHONY: terraform-destroy
terraform-destroy: ## Destroy terraform
	@cd terraform && terraform destroy $(TFM_VARS) && cd ..

# -----------------------------------------------------------------
#    AWS ECR targets
# -----------------------------------------------------------------
.PHONY: ecr-push-all
ecr-push-all: ecr-push-aggregator ecr-push-reporter ecr-push-runner ## Push ecr repository (all images)

.PHONY: ecr-push-aggregator
ecr-push-aggregator: ## Push ecr repository (gatling-aggregate-runner image)
	@$(SBT_CMD) gatling-aggregate-runner/ecr:push

.PHONY: ecr-push-reporter
ecr-push-reporter: ## Push ecr repository (gatling-s3-reporter image)
	@cd gatling-s3-reporter && make release AWS_PROFILE=$(AWS_PROFILE) AWS_REGION=$(AWS_REGION) && cd ..

.PHONY: ecr-push-runner
ecr-push-runner: ## Push ecr repository (gatling-runner image)
	@$(SBT_CMD) gatling-runner/ecr:push

# -----------------------------------------------------------------
#    Setup targets
# -----------------------------------------------------------------

.PHONY: setup
setup: ## Setup tools
ifeq ($(shell $(SBT_CMD) -version 2> /dev/null),)
	@mkdir -p $(TOOLS_DIR)
	@curl -sSL -o - https://github.com/sbt/sbt/releases/download/v$(SBT_VERSION)/sbt-$(SBT_VERSION).tgz | tar zxf - -C $(TOOLS_DIR)/
endif
	@$(SBT_CMD) -version
