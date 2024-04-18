#!/bin/bash
set -e

BASENAME=$(basename "$0")
SG_EXAM_APP="sg-exam-app"
SG_EXAM_NEXT_ADMIN="sg-exam-next-admin"
SG_EXAM_NEXT_APP="sg-exam-next-app"
SG_EXAM_USER_SERVICE="sg-user-service"

function update_version() {
  local version="$1"
  echo "Updating project version to $version ..."
  sed -i "" "s/version = '[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}'/version = '$version'/" build.gradle
  sed -i "" "s/version=[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}/version=$version/" gradle.properties
  sed -i "" "s/SG_EXAM_VERSION=[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}/SG_EXAM_VERSION=$version/" .env
  sed -i "" "s/version-[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}/version-$version/" README.md
  cd sg-api && update_build "$version"
  cd ../sg-common && update_build "$version"
  cd ../sg-exam-service && update_build "$version"
  cd ../sg-generator && update_build "$version"
  cd ../sg-job && update_build "$version"
  cd ../sg-user-service && update_build "$version"
  cd ../frontend
  cd sg-exam-app && update_package_json "$version"
  cd ../sg-exam-next-admin && update_package_json "$version"
  cd ../sg-exam-next-app && update_package_json "$version"
  cd ../..
  echo "Project version has been updated to $version successfully."
}

function update_build() {
  local version="$1"
  sed -i "" "s/version '[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}'/version '$version'/" build.gradle
}

function update_package_json() {
  local version="$1"
  sed -i "" "s/\"version\": \"[0-9]\{1,2\}.[0-9]\{1,2\}.[0-9]\{1,2\}\"/\"version\": \"$version\"/" package.json
}

function build_web() {
  echo "Building $SG_EXAM_APP ..."
  # shellcheck disable=SC2164
  cd frontend/sg-exam-app
  yarn build
  cd ../..
  echo "$SG_EXAM_APP has been built successfully."
}

function build_admin() {
  echo "Building $SG_EXAM_NEXT_ADMIN ..."
  # shellcheck disable=SC2164
  cd frontend/sg-exam-next-admin
  yarn build
  cd ../..
  echo "$SG_EXAM_NEXT_ADMIN has been built successfully."
}

function build_app() {
  echo "Building $SG_EXAM_NEXT_APP ..."
  # shellcheck disable=SC2164
  cd frontend/sg-exam-next-app
  export NODE_OPTIONS=--openssl-legacy-provider && yarn build:weapp
  cd ../..
  echo "$SG_EXAM_NEXT_APP has been built successfully."
}

function build_frontend() {
  build_web
  build_admin
  build_app
}

function build_service() {
  local version="$1"
  echo "Building $SG_EXAM_USER_SERVICE $version ..."
  chmod 764 gradlew
  ./gradlew build -x test
  echo "$SG_EXAM_USER_SERVICE has been built successfully."
  echo "Building docker image ..."
  docker build -t registry.cn-hangzhou.aliyuncs.com/sg-exam-next/sg-exam:"$version" -t registry.cn-hangzhou.aliyuncs.com/sg-exam-next/sg-exam:latest .
  echo "Docker image has been built successfully."
}

function push_service() {
  local version="$1"
  echo "Pushing docker image ..."
  docker push registry.cn-hangzhou.aliyuncs.com/sg-exam-next/sg-exam:"$version"
  docker push registry.cn-hangzhou.aliyuncs.com/sg-exam-next/sg-exam:latest
  echo "Docker image has been pushed successfully."
}

function start_service() {
  echo "Pulling docker image ..."
  docker-compose pull
  echo "Starting services ..."
  docker-compose config
  docker-compose up --remove-orphans --no-build -d
  echo "Services has been started successfully."
  docker ps
  logs
}

function start_service_inner() {
  echo "Starting nginx service ..."
  mkdir -p /apps/data/web/working/logs/nginx
  cd /usr/sbin
  ./nginx
  echo "Nginx service started."
  cd /apps/data/web/working
  java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Duser.timezone=$TZ -Dfile.encoding=UTF-8 -jar app.jar
}

function stop_service() {
  echo "Stopping services ..."
  docker-compose stop -t 60
  echo "Services has been stopped successfully."
}

function logs() {
  docker logs "$(docker ps |grep $SG_EXAM_USER_SERVICE|awk '{print $1}')" -f --tail=100
}

function install_jdk() {
  echo ""
}

function install_docker() {
    yum install -y yum-utils device-mapper-persistent-data lvm2
    yum-config-manager --add-repo http://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
    yum makecache fast
    yum -y install docker-ce
    systemctl enable docker
    systemctl status docker
    service docker start
}

function setup() {
  echo "Start to setup, current directory: $(pwd)"
  if [ -d "sg-exam" ]; then
    echo "Directory sg-exam is exists."
    return
  fi
  mkdir -p sg-exam
  echo "Create directory sg-exam."
  cd sg-exam
  wget https://gitee.com/wells2333/sg-exam/raw/master/setup.sh
  wget https://gitee.com/wells2333/sg-exam/raw/master/.env
  wget https://gitee.com/wells2333/sg-exam/raw/master/docker-compose.yml
  # 删除所有 build 的配置
  sed -e "/build:/d" docker-compose.yml > docker-compose.yml

  echo "Create directory config-repo."
  mkdir -p config-repo
  cd config-repo
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/application.yml
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/prometheus.yml
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/sg-user-service.yml

  echo "Create directory env."
  mkdir -p env

  echo "Create directory mysql."
  mkdir -p mysql

  echo "Create directory nginx."
  mkdir -p nginx

  echo "Create directory redis."
  mkdir -p redis

  cd env
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/env/sg-user-service.env
  cd ..
  cd mysql
  wget https://gitee.com/wells2333/sg-exam/blob/master/config-repo/mysql/init.sql
  wget https://gitee.com/wells2333/sg-exam/blob/master/config-repo/mysql/update.sql
  cd ..
  cd nginx
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/nginx/nginx.conf
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/nginx/nginx_ssl.conf
  cd ..

  cd redis
  wget https://gitee.com/wells2333/sg-exam/raw/master/config-repo/redis/redis.conf
  cd ..
  echo "Setup finished."
}

function print_usage() {
  echo "Usage: $BASENAME COMMAND
       $BASENAME -help
  A online examination application.
  Commands:
      -help                Get detailed help and usage
      -build_f             Build frontend services
      -build               Build backend services and docker image
      -push                Push docker image to registry
      -start               Start services
      -stop                Stop services
      -restart             Pull docker image and restart services
      -logs                Tails the services logs
      -version             Update project version to a specify version
      -setup               Setup config directory from git"
  exit 1
}

function main() {
  if [ "$#" -eq 0 ]; then
    _usage
    exit 0
  fi
  local cmd="$1"
  local version="$2"
  shift
  case "${cmd}" in
  -h | --help | help)
    print_usage
    exit
    ;;
  -build_f | --build_f | build_f)
    build_frontend
    ;;
  -build | --build | build)
    build_service "$version"
    ;;
  -push | --push | push)
    push_service "$version"
    ;;
  -start | --start | start)
    start_service
    ;;
  -start_inner | --start_inner | start_inner)
    start_service_inner
    ;;
  -stop | --stop | stop)
    stop_service
    ;;
  -restart | --restart | restart)
    stop_service
    start_service
    ;;
  -logs | --logs | logs)
    logs
    ;;
  -version | --version | version)
    update_version "$version"
    ;;
  -setup | --setup | setup)
    setup
    ;;
  *)
    echo "$BASENAME: '$cmd' is not a $BASENAME command."
    echo "Run '$BASENAME -help' for more information."
    exit 1
    ;;
  esac
}
main "$@"
