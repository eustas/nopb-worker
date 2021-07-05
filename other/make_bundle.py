import sys

output = []
for i in range(2, len(sys.argv)):
  with open(sys.argv[i], "rb") as f:
    data = bytearray(f.read())
    dataLen = len(data)
    while dataLen >= 128:
      output.append(bytearray([128 + (dataLen & 0x7F)]))
      dataLen = dataLen >> 7
    output.append(bytearray([dataLen]))
    output.append(data)
with open(sys.argv[1], "wb") as f:
  for chunk in output:
    f.write(chunk)
