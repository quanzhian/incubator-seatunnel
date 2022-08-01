# Socket
## Description

Used to read data from Socket. Both support streaming and batch mode.

##  Options

| name | type   | required | default value |
| --- |--------| --- | --- |
| host | String | No | localhost |
| port | Integer | No | 9999 |

### host [string]
socket server host

### port [integer]

socket server port

## Example

simple:

```hocon
Socket {
        host = "localhost"
        port = 9999
    }
```

test:

* Configuring the SeaTunnel config file

```hocon
env {
  execution.parallelism = 1
  job.mode = "STREAMING"
}

source {
    Socket {
        host = "localhost"
        port = 9999
    }
}

transform {
}

sink {
  Console {}
}

```

* Start a port listening

```shell
nc -l 9999
```

* Start a SeaTunnel task

* Socket Source send test data

```text
~ nc -l 9999
test
hello
flink
spark
```

* Console Sink print data

```text
[test]
[hello]
[flink]
[spark]
```
