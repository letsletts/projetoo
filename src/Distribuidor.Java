import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Distribuidor {

    // Configure aqui os IPs e Portas dos seus Receptores
    private static final String[] RECEPTORS = {
        "127.0.0.1:12345", // Exemplo local
        //"192.168.0.10:12345", 
        //"192.168.0.11:12345"
    };

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Distribuidor de Ordenação ===");

        // 1. Definir tamanho do vetor
        System.out.print("Digite o tamanho do vetor (ex: 1000000): ");
        int tamanho = 0;
        try {
            tamanho = Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            tamanho = 100_000; // Valor padrão
            System.out.println("Valor inválido. Usando 100.000.");
        }

        // 2. Gerar vetor aleatório
        System.out.println("Gerando vetor aleatório...");
        byte[] vetorOriginal = new byte[tamanho];
        Random rnd = new Random();
        rnd.nextBytes(vetorOriginal); // Preenche com bytes aleatórios

        // Medição de tempo do distribuído
        long inicioDist = System.currentTimeMillis();

        // 3. Dividir e Enviar para Receptores
        List<byte[]> partesOrdenadasRecebidas = processarDistribuido(vetorOriginal);
        
        // 4. Merge Final (Juntar as partes recebidas dos servidores)
        System.out.println("[D] Realizando merge final das partes recebidas...");
        while (partesOrdenadasRecebidas.size() > 1) {
            byte[] p1 = partesOrdenadasRecebidas.remove(0);
            byte[] p2 = partesOrdenadasRecebidas.remove(0);
            byte[] merged = MergeUtils.intercalar(p1, p2);
            partesOrdenadasRecebidas.add(merged);
        }

        byte[] vetorFinalOrdenado = partesOrdenadasRecebidas.get(0);
        long fimDist = System.currentTimeMillis();
        
        System.out.printf("[D] Ordenação Distribuída concluída em %.3f segundos.%n", (fimDist - inicioDist) / 1000.0);

        // 5. Salvar em arquivo texto (Requisito do PDF)
        System.out.print("Digite o nome do arquivo para salvar o resultado (ex: saida.txt): ");
        String nomeArquivo = scanner.nextLine();
        salvarEmArquivo(vetorFinalOrdenado, nomeArquivo);

        // 6. Validação (Opcional, mas bom para garantir)
        System.out.println("Verificando se está realmente ordenado...");
        boolean ordenado = true;
        for(int i=0; i<vetorFinalOrdenado.length-1; i++){
            if(vetorFinalOrdenado[i] > vetorFinalOrdenado[i+1]){
                ordenado = false;
                break;
            }
        }
        System.out.println("Status da Ordenação: " + (ordenado ? "SUCESSO" : "FALHA"));
        
        scanner.close();
    }

    private static List<byte[]> processarDistribuido(byte[] vetorTotal) {
        int numServidores = RECEPTORS.length;
        List<byte[]> resultados = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        
        // Dividir o vetor para os servidores
        int tamanhoBloco = vetorTotal.length / numServidores;

        for (int i = 0; i < numServidores; i++) {
            final int index = i;
            int inicio = i * tamanhoBloco;
            int fim = (i == numServidores - 1) ? vetorTotal.length : (i + 1) * tamanhoBloco;
            
            byte[] fatia = Arrays.copyOfRange(vetorTotal, inicio, fim);
            
            Thread t = new Thread(() -> {
                String[] ipPort = RECEPTORS[index].split(":");
                String host = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);

                try (Socket socket = new Socket(host, port);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                    System.out.printf("[D] Enviando %d elementos para %s:%d%n", fatia.length, host, port);
                    
                    // Envia Pedido
                    out.writeObject(new Pedido(fatia));
                    out.flush();

                    // Recebe Resposta
                    Object response = in.readObject();
                    if (response instanceof Resposta) {
                        Resposta r = (Resposta) response;
                        synchronized (resultados) {
                            resultados.add(r.getVetorOrdenado());
                        }
                        System.out.printf("[D] Recebido vetor ordenado de %s:%d%n", host, port);
                    }

                    // Encerra conexão com este servidor
                    out.writeObject(new ComunicadoEncerramento());

                } catch (Exception e) {
                    System.err.printf("[D] Falha ao conectar com %s: %s%n", host, e.getMessage());
                }
            });
            
            threads.add(t);
            t.start();
        }

        // Aguardar todas as threads terminarem
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException e) {}
        }

        return resultados;
    }

    private static void salvarEmArquivo(byte[] vetor, String nomeArquivo) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(nomeArquivo))) {
            writer.println("Vetor Ordenado:");
            writer.println(Arrays.toString(vetor)); // Salva no formato [1, 2, 3...]
            System.out.println("Arquivo salvo com sucesso: " + nomeArquivo);
        } catch (IOException e) {
            System.err.println("Erro ao salvar arquivo: " + e.getMessage());
        }
    }
}