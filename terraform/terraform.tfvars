profile = "xxxxxx"
region  = "ap-northeast-1"
prefix  = "cloud-gatling-tools"
owner   = "gatlingtools"

vpc_name = "cloud_gatling_vpc"

aws_az = ["ap-northeast-1a", "ap-northeast-1c", "ap-northeast-1d"]
aws_subnet_public = ["10.0.0.0/24", "10.0.1.0/24", "10.0.2.0/24"]

# variables for gatling
gatling_ecs_cluster_name          = "cloud-gatling-tools-ecs"
gatling_runner_ecr_name           = "cloud-gatling-tools/gatling-runner"
gatling_s3_reporter_ecr_name      = "cloud-gatling-tools/gatling-s3-reporter"
gatling_aggregate_runner_ecr_name = "cloud-gatling-tools/gatling-aggregate-runner"
gatling_s3_log_bucket_name        = "cloud-gatling-tools-logs"
gatling_s3_log_bucket_ip_list     = []
