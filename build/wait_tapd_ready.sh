i="."
while [[ 1 ]]; do
  if [[ $i == "............" ]]; then
    break
  fi
  sleep 10
  i=$i"."
  docker logs tapd
  docker logs tapd|grep register_connector_complete
  if [[ $? -eq 0 ]]; then
    echo "tapdata container ready!"
    break
  fi
  echo "still wait tapdata container ready..."
done