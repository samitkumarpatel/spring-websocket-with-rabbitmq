docker run --rm \
  -p 5672:5672 \
  -p 15672:15672 \
  -p 61613:61613 \
  -p 15674:15674 \
  rabbitmq:3-management sh -c 'rabbitmq-plugins enable rabbitmq_stomp rabbitmq_web_stomp && rabbitmq-server'