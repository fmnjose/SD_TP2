# SD_TP2
First Project for SD (Sistemas Distribuidos)

Segurança (máx: TBD valores)
O objetivo desta funcionalidade é tornar o sistema seguro, impedindo que elementos não autorizados executem operações no sistema. Para alcançar este objetivo, a solução deve incluir os seguintes mecanismos:

1 - utilizar canais seguros, usando TLS, com autenticação do servidor. As operações dos clientes incluem uma password para verificar que o cliente está autorizado a efetuar a operação indicada (esta última parte já se verificava nas interfaces definidas no sistema);

2 - confirmar que as operações executadas entre os servidores não podem ser invocadas por um cliente – sugere-se a utilização dum segredo partilhado entre os servidores.