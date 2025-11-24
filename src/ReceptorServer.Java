import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ReceptorServer {
    private static final int PORT = 12345; // Porta padrão
    private static final int BACKLOG = 50;

    public static void main(String[] args) {
        int port = PORT;
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); } catch (Exception e) {}
        }

        System.out.printf("[R] Iniciando Receptor em porta %d%n", port);

        try (ServerSocket serverSocket = new ServerSocket(port, BACKLOG)) {
            while (true) {
                Socket client = serverSocket.accept();
                System.out.printf("[R] Conexão aceita de %s:%d%n",
                        client.getInetAddress().getHostAddress(), client.getPort());
                // Inicia thread para lidar com essa conexão específica
                new Thread(new ConnectionHandler(client)).start();
            }
        } catch (IOException e) {
            System.err.println("[R] Erro no servidor: " + e.getMessage());
        }
    }

    static class ConnectionHandler implements Runnable {
        private final Socket socket;
        
        ConnectionHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                Object obj;
                while ((obj = in.readObject()) != null) {
                    
                    if (obj instanceof Pedido) {
                        Pedido pedido = (Pedido) obj;
                        byte[] vetorRecebido = pedido.getNumeros();
                        
                        System.out.printf("[R] Pedido recebido com %d elementos. Processando...%n", vetorRecebido.length);
                        long t0 = System.currentTimeMillis();

                        // --- Lógica de Paralelismo Interno do Receptor ---
                        byte[] vetorResultado = processarParalelamente(vetorRecebido);
                        
                        long t1 = System.currentTimeMillis();
                        System.out.printf("[R] Ordenação finalizada (tempo %d ms). Enviando resposta...%n", (t1 - t0));

                        Resposta resp = new Resposta(vetorResultado);
                        out.writeObject(resp);
                        out.flush();
                        out.reset(); // Importante para limpar cache de objetos
                    }
                    else if (obj instanceof ComunicadoEncerramento) {
                        System.out.println("[R] ComunicadoEncerramento recebido. Fechando conexão.");
                        break;
                    }
                }
            } catch (EOFException e) {
                System.out.println("[R] Cliente desconectou.");
            } catch (Exception e) {
                System.err.println("[R] Erro: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }

        // Método que divide o vetor, ordena em threads e faz o merge
        private byte[] processarParalelamente(byte[] vetorCompleto) throws InterruptedException, ExecutionException {
            int numProcessadores = Runtime.getRuntime().availableProcessors();
            // Se o vetor for pequeno, não compensa dividir
            if (vetorCompleto.length < 1000 || numProcessadores <= 1) {
                Arrays.sort(vetorCompleto);
                return vetorCompleto;
            }

            ExecutorService pool = Executors.newFixedThreadPool(numProcessadores);
            List<Future<byte[]>> futures = new ArrayList<>();

            // 1. Divisão: Quebra o vetor em partes iguais para cada processador
            int tamanhoBloco = vetorCompleto.length / numProcessadores;
            
            for (int i = 0; i < numProcessadores; i++) {
                int inicio = i * tamanhoBloco;
                int fim = (i == numProcessadores - 1) ? vetorCompleto.length : (i + 1) * tamanhoBloco;
                
                // Copia a parte correspondente
                byte[] parte = Arrays.copyOfRange(vetorCompleto, inicio, fim);
                
                // 2. Ordenação: Cada thread ordena sua parte
                futures.add(pool.submit(() -> {
                    Arrays.sort(parte);
                    return parte;
                }));
            }

            // Coleta os resultados ordenados
            List<byte[]> partesOrdenadas = new ArrayList<>();
            for (Future<byte[]> f : futures) {
                partesOrdenadas.add(f.get());
            }
            pool.shutdown();

            // 3. Merge (Intercalação): Junta as partes 2 a 2 até sobrar uma
            while (partesOrdenadas.size() > 1) {
                byte[] p1 = partesOrdenadas.remove(0);
                byte[] p2 = partesOrdenadas.remove(0);
                
                // Faz o merge
                byte[] uniao = MergeUtils.intercalar(p1, p2);
                
                // Adiciona no final da fila para ser processado novamente se necessário
                partesOrdenadas.add(uniao);
            }

            return partesOrdenadas.get(0);
        }
    }
}