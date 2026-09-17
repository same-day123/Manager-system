#!/usr/bin/env bash
# RuoYi-Vue-fast 构建辅助脚本（WorkBuddy 用）
# 本机 mvn 启动脚本损坏（classworlds 找不到），改为直接调用 classworlds 启动器。
# 用法： bash .workbuddy/tools/mvnx.sh -o -B compile
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

MVN_HOME='D:\IDEA\apache-maven-3.9.4'
JAVA_EXE="/d/Study_Running/JDK_Warehouse/jdk-17.0.19/bin/java.exe"
PROJECT_DIR="/d/code/Manager_system/RuoYi-Vue-fast"

cd "$PROJECT_DIR" || exit 1

"$JAVA_EXE" \
  -classpath "$MVN_HOME\\boot\\plexus-classworlds-2.7.0.jar" \
  -Dclassworlds.conf="$MVN_HOME\\bin\\m2.conf" \
  -Dmaven.home="$MVN_HOME" \
  -Dmaven.multiModuleProjectDirectory="$(cygpath -w "$PWD")" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
