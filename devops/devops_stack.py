from aws_cdk import (core as cdk, 
                     aws_ec2 as ec2, 
                     aws_ecs as ecs,
                     aws_ecs_patterns as ecs_patterns,
                     aws_iam as iam,
                     aws_ecr as ecr,
                     aws_elasticloadbalancingv2 as elb,
                     aws_logs as logs,
                     aws_sns as sns,
                     aws_sns_subscriptions as sub,
                     aws_cloudwatch as cloudwatch,
                     aws_cloudwatch_actions as cloudwatch_actions,
                     )

# For consistency with other languages, `cdk` is the preferred import name for
# the CDK's core module.  The following line also imports it as `core` for use
# with examples from the CDK Developer's Guide, which are in the process of
# being updated to use `cdk`.  You may delete this import if you don't need it.


class DevopsStack(cdk.Stack):

    def __init__(self, scope: cdk.Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)
        #default is all AZs in region
        vpc = ec2.Vpc(self, "MyVpc", max_azs=2)     # default is all AZs in region
        
        #create ecr
        repository=ecr.Repository(
           self,
           'devpos',
           repository_name='devops',
        )  


        #create ecs cluster
        cluster = ecs.Cluster(self, "MyCluster", vpc=vpc)
        
        #create ecs task execution role which make sure it could pull image from ecr
        execution_role = iam.Role(
            self,
            "ecs-execution-role",
            assumed_by=iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
            role_name="ecs-execution-role")
        
        execution_role.add_to_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            resources=['*'],
            actions=[
                'ecr:GetAuthorizationToken',
                'ecr:BatchCheckLayerAvailability',
                'ecr:GetDownloadUrlForLayer',
                'ecr:BatchGetImage',
                'logs:CreateLogStream',
                'logs:PutLogEvents'
            ],
        ))


        #create snd and email subscription
        my_topic = sns.Topic(self, "MyTopic")
        my_topic.add_subscription(sub.EmailSubscription("wyifei@163.com"))
        
        #crete log group
        log_group = logs.LogGroup(
            self,
            "log_group",
            log_group_name="web-logs",
        )

        #create metrid and link to log group
        metric=cloudwatch.Metric(
            metric_name="error-log",
            namespace="error-log-namespace",    
                )

        log_group.add_metric_filter(
            "filter-error-log",
            filter_pattern=logs.FilterPattern.literal('ERROR'),
            metric_name="error-log",
            metric_namespace="error-log-namespace",
        )

        alarm=metric.create_alarm(
            self,
            "error trigger email",
            evaluation_periods=1,
            threshold=10,
            statistic="sum",
            )

        alarm.add_alarm_action(cloudwatch_actions.SnsAction(my_topic))
        #create ecs service,loadbalancer
        load_balanced_fargate_service=ecs_patterns.ApplicationLoadBalancedFargateService(self, "MyFargateService",
            cluster=cluster,            # Required
            cpu=512,                    # Default is 256
            memory_limit_mib=2048,      # Default is 512
            desired_count=2,            # Default is 1
            task_image_options=ecs_patterns.ApplicationLoadBalancedTaskImageOptions(
                #image=ecs.ContainerImage.from_ecr_repository(repository=repository,tag='latest'),
                image=ecs.ContainerImage.from_registry("amazon/amazon-ecs-sample"),
                container_port=80,
                execution_role=execution_role,
                log_driver=ecs.LogDriver.aws_logs(stream_prefix="web_logs",log_group=log_group)
                ),
            deployment_controller={"type": ecs.DeploymentControllerType.CODE_DEPLOY}, #aws codedeploy require the service deployment type to be blue/green
            #ecs.DeploymentController(type=ecs.DeploymentControllerType.CODE_DEPLOY), #AWS CodeDeploy require the service deployment type should be blue/green
           )
        target2 = elb.ApplicationTargetGroup(self, "TG2",target_type=elb.TargetType.IP,port=8080,vpc=load_balanced_fargate_service.cluster.vpc)
        listener=load_balanced_fargate_service.load_balancer.add_listener("Listener2",default_target_groups=[target2],port = 8080,)

        #create 

