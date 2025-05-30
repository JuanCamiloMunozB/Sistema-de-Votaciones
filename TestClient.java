import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData; // Necesario para pasar propiedades
import com.zeroc.Ice.Properties;         // Necesario para crear propiedades
import com.zeroc.Ice.Util;
import ElectionSystem.ServerServicePrx;
import ElectionSystem.ElectionData;
import ElectionSystem.VoteData;
import ElectionSystem.CandidateData;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestClient {
  public static void main(String[] args) {
    // Alternativa más robusta: Cargar la configuración desde un archivo
    InitializationData initData = new InitializationData();
    initData.properties = Util.createProperties();
    // Intenta cargar desde un archivo de propiedades específico.
    // El archivo config.client debe estar en el directorio de trabajo o especificar una ruta.
    try {
        initData.properties.load("config.client"); 
    } catch (Exception e) {
        System.err.println("Advertencia: No se pudo cargar config.client. " + e.getMessage());
        // Si no se carga, el locator deberá definirse de otra forma (ej. variable de entorno o -D)
        // o se usará el default de Ice si no se especifica locator y falla.
        // Para asegurar, podemos setearlo aquí si falla la carga y no queremos depender de -D
         if (System.getProperty("Ice.Default.Locator") == null) {
             System.out.println("Estableciendo Ice.Default.Locator por defecto en código ya que config.client no se cargó y -D no está presente.");
             initData.properties.setProperty("Ice.Default.Locator", "IceGrid/Locator:tcp -h localhost -p 4062");
         }
    }

    // Pasar args también permite que se sobreescriban propiedades desde la línea de comando si es necesario
    // y también carga el archivo de configuración por defecto si Ice.Config no está seteado.
    // String[] iceArgs = new String[] { "--Ice.Config=config.client" }; // Podrías forzarlo así
    // try (Communicator communicator = Util.initialize(iceArgs, initData)) { 
    try (Communicator communicator = Util.initialize(args, initData)) { // Usar args originales y initData con propiedades cargadas
      
      System.out.println("Usando Locator: " + communicator.getProperties().getProperty("Ice.Default.Locator"));
      System.out.println("Intentando obtener proxy para ServerService...");
      ServerServicePrx server = ServerServicePrx.checkedCast(
          communicator.stringToProxy("ServerService")); 

      if (server == null) {
        System.err.println("Proxy nulo para ServerService. ¿Está IceGrid Registry corriendo y la app desplegada?");
        return;
      }
      System.out.println("Proxy a ServerService obtenido.");

      // ... (resto de tu código de prueba)
      System.out.println("Llamando a getElectionData(0) en ServerService...");
      ElectionData electionData = server.getElectionData(0); 

      if (electionData != null) {
        System.out.println("Respuesta de getElectionData:");
        System.out.println("  Nombre Elección: " + electionData.name);
        // ... resto de los prints ...
      } else {
        System.err.println("getElectionData devolvió null.");
      }
      System.out.println("Llamada a ServerService completada.");

      System.out.println("Intentando obtener proxy para ControlCenterService...");
      ElectionSystem.ControlCenterServicePrx controlCenter = ElectionSystem.ControlCenterServicePrx.checkedCast(
          communicator.stringToProxy("ControlCenterService"));
      if (controlCenter == null) {
          System.err.println("Proxy nulo para ControlCenterService.");
      } else {
          System.out.println("Proxy a ControlCenterService obtenido.");
          
          // Test getting candidates first
          try {
              System.out.println("Obteniendo lista de candidatos...");
              CandidateData[] candidates = controlCenter.getCandidates();
              System.out.println("Candidatos disponibles (" + candidates.length + "):");
              for (CandidateData candidate : candidates) {
                  System.out.println("  ID: " + candidate.id + ", Nombre: " + candidate.firstName + " " + candidate.lastName + ", Partido: " + candidate.party);
              }
              
              if (candidates.length == 0) {
                  System.err.println("No hay candidatos disponibles en el sistema. No se puede emitir voto.");
                  return;
              }
              
              // Get voting tables to find valid citizen and table IDs
              System.out.println("Obteniendo datos de mesas de votación...");
              ElectionSystem.VotingTableData[] votingTables = server.getVotingTablesFromStation(1); // Try station 1
              System.out.println("Mesas encontradas para estación 1: " + votingTables.length);
              
              if (votingTables.length == 0) {
                  System.out.println("No hay mesas en estación 1, intentando con estación 0...");
                  votingTables = server.getVotingTablesFromStation(0);
                  System.out.println("Mesas encontradas para estación 0: " + votingTables.length);
              }
              
              if (votingTables.length > 0) {
                  System.out.println("Primera mesa ID: " + votingTables[0].id);
                  System.out.println("Ciudadanos en primera mesa: " + votingTables[0].citizens.length);
                  
                  if (votingTables[0].citizens.length > 0) {
                      for (int i = 0; i < Math.min(3, votingTables[0].citizens.length); i++) {
                          ElectionSystem.CitizenData citizen = votingTables[0].citizens[i];
                          System.out.println("  Ciudadano " + i + ": " + citizen.firstName + " " + citizen.lastName + " (ID: " + citizen.id + ")");
                      }
                  }
              }
              
              if (votingTables.length > 0 && votingTables[0].citizens.length > 0 && candidates.length > 0) {
                  // Use the first available citizen and candidate
                  ElectionSystem.CitizenData firstCitizen = votingTables[0].citizens[0];
                  CandidateData firstCandidate = candidates[0];
                  int tableId = votingTables[0].id;
                  
                  System.out.println("Usando datos reales del sistema:");
                  System.out.println("  Ciudadano: " + firstCitizen.firstName + " " + firstCitizen.lastName + " (ID: " + firstCitizen.id + ")");
                  System.out.println("  Candidato: " + firstCandidate.firstName + " " + firstCandidate.lastName + " (ID: " + firstCandidate.id + ")");
                  System.out.println("  Mesa: " + tableId);
                  
                  // Test emitting a vote with real data
                  System.out.println("Emitiendo un voto de prueba...");
                  VoteData testVote = new VoteData(
                      firstCitizen.id,  // Use real citizen ID
                      firstCandidate.id,  // Use real candidate ID
                      tableId,  // Use real table ID
                      LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
                  );
                  
                  try {
                      controlCenter.submitVote(testVote);
                      System.out.println("Voto emitido exitosamente:");
                      System.out.println("  Ciudadano ID: " + testVote.citizenId);
                      System.out.println("  Candidato ID: " + testVote.candidateId);
                      System.out.println("  Mesa ID: " + testVote.tableId);
                      System.out.println("  Timestamp: " + testVote.timestamp);
                      
                      // Wait a moment to see if any events are received
                      System.out.println("Esperando eventos por 3 segundos...");
                      Thread.sleep(3000);
                      
                  } catch (Exception e) {
                      System.err.println("Error emitiendo voto: " + e.getMessage());
                      e.printStackTrace();
                  }
              } else {
                  System.err.println("No se encontraron datos válidos para emitir un voto:");
                  System.err.println("  Mesas encontradas: " + votingTables.length);
                  if (votingTables.length > 0) {
                      System.err.println("  Ciudadanos en primera mesa: " + votingTables[0].citizens.length);
                  }
                  System.err.println("  Candidatos encontrados: " + candidates.length);
                  
                  System.out.println("SOLUCIÓN: Asegúrese de que la base de datos contiene:");
                  System.out.println("  1. Al menos un candidato");
                  System.out.println("  2. Al menos una mesa de votación");
                  System.out.println("  3. Al menos un ciudadano asignado a una mesa");
              }
              
          } catch (Exception e) {
              System.err.println("Error obteniendo datos del sistema: " + e.getMessage());
              e.printStackTrace();
          }
      }

    } catch (com.zeroc.Ice.LocalException e) {
      System.err.println("Error de Ice localizando o comunicando con ServerService: " + e);
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Error inesperado en TestClient: " + e);
      e.printStackTrace();
    }
  }
}