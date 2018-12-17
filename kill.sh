#!/bin/bash
file=$(grep "spring.application.name" src/main/resources/bootstrap.properties )
IFS='=' read -r -a array1 <<< "$file"
echo "${array1[-1]}"

port=$(grep "server.port" /home/fefe/Desktop/config/${array1[-1]}.properties )
IFS='=' read -r -a array <<< "$port"
echo "${array[-1]}"
var=$(lsof -i :${array[-1]})
echo $var

if [ -z "$var" ]
then
      echo "no tomcat running"
else
      kill -9 $(lsof -i :${array[-1]} | awk '{if (NR!=1) {print $2}}')
fi

