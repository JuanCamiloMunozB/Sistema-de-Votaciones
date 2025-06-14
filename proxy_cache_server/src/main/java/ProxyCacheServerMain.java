import com.zeroc.Ice.*;
import com.zeroc.Ice.Exception;
import ElectionSystem.*;

public class ProxyCacheServerMain {
    
    public static void main(String[] args) {
        ProxyCacheService proxyCacheService = null;
        
        try (Communicator communicator = Util.initialize(args, "config.proxy.cfg")) {
            
            ServerServicePrx serverProxy = null;
            
            System.out.println("Conectando a IceGrid para buscar servidores disponibles...");
            
            try {
                // Strategy 1: Use IceGrid Query interface to find servers directly
                System.out.println("  Usando IceGrid Query para encontrar ServerService...");
                ObjectPrx queryBase = communicator.stringToProxy("IceGrid/Query");
                if (queryBase != null) {
                    com.zeroc.IceGrid.QueryPrx query = com.zeroc.IceGrid.QueryPrx.checkedCast(queryBase);
                    if (query != null) {
                        System.out.println("  Buscando objetos ServerService...");
                        ObjectPrx[] objects = query.findAllObjectsByType("::ElectionSystem::ServerService");
                        System.out.println("  Encontrados " + objects.length + " objetos ServerService");
                        
                        if (objects.length > 0) {
                            // Try the load-balanced proxy first
                            System.out.println("    Probando proxy load-balanced: " + objects[0].toString());
                            try {
                                System.out.println("      Haciendo ping al objeto...");
                                objects[0].ice_ping();
                                System.out.println("      ✓ Ping exitoso");
                                
                                System.out.println("      Realizando checkedCast...");
                                serverProxy = ServerServicePrx.checkedCast(objects[0].ice_timeout(5000));
                                
                                if (serverProxy != null) {
                                    System.out.println("      ✓ CheckedCast exitoso");
                                    
                                    // Test the connection
                                    System.out.println("      Probando método getCandidates...");
                                    CandidateData[] candidates = serverProxy.getCandidates();
                                    System.out.println("    ✓ Verificación completada. Candidatos: " + candidates.length);
                                } else {
                                    System.out.println("      ✗ CheckedCast devolvió null");
                                }
                            } catch (com.zeroc.Ice.NoEndpointException e) {
                                System.out.println("    ✗ Error con load balancer: NoEndpointException - Los servidores no tienen endpoints activos");
                                System.out.println("      Esto indica que los adaptadores están registrados pero sin endpoints TCP");
                                
                                // Wait a bit for servers to fully activate
                                System.out.println("      Esperando 5 segundos para que los servidores terminen de activarse...");
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    System.out.println("      Espera interrumpida");
                                }
                                
                                // Try again after waiting
                                try {
                                    System.out.println("      Reintentando después de espera...");
                                    objects[0].ice_ping();
                                    serverProxy = ServerServicePrx.checkedCast(objects[0].ice_timeout(5000));
                                    if (serverProxy != null) {
                                        CandidateData[] candidates = serverProxy.getCandidates();
                                        System.out.println("    ✓ Conexión exitosa después de espera. Candidatos: " + candidates.length);
                                    }
                                } catch (Exception retryEx) {
                                    System.out.println("      ✗ Reintento falló: " + retryEx.getClass().getSimpleName());
                                }
                            } catch (Exception e) {
                                System.out.println("    ✗ Error con load balancer: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                                
                                // Try individual replicas if load balancer fails
                                for (int i = 0; i < objects.length && serverProxy == null; i++) {
                                    try {
                                        System.out.println("    Probando réplica individual " + (i + 1) + ": " + objects[i].toString());
                                        ObjectPrx replica = objects[i].ice_timeout(3000);
                                        replica.ice_ping();
                                        ServerServicePrx testProxy = ServerServicePrx.checkedCast(replica);
                                        if (testProxy != null) {
                                            CandidateData[] testCandidates = testProxy.getCandidates();
                                            serverProxy = testProxy;
                                            System.out.println("    ✓ Conectado a réplica " + (i + 1) + ". Candidatos: " + testCandidates.length);
                                            break;
                                        }
                                    } catch (Exception ex) {
                                        System.out.println("    ✗ Error con réplica " + (i + 1) + ": " + ex.getMessage());
                                    }
                                }
                            }
                        } else {
                            System.out.println("  ✗ No se encontraron objetos ServerService registrados");
                        }
                    } else {
                        System.out.println("  ✗ No se pudo obtener IceGrid Query interface");
                    }
                } else {
                    System.out.println("  ✗ No se pudo obtener proxy para IceGrid/Query");
                }
                
                // Strategy 2: Fallback to direct adapter connections if Query didn't work
                if (serverProxy == null) {
                    System.out.println("  Fallback: Intentando conexión directa a adaptadores...");
                    String[] adapterIds = {"ServerAdapter1ID", "ServerAdapter2ID"};
                    for (String adapterId : adapterIds) {
                        try {
                            System.out.println("    Intentando adaptador: " + adapterId);
                            ObjectPrx adapterBase = communicator.stringToProxy("ServerService@" + adapterId);
                            if (adapterBase != null) {
                                System.out.println("      Proxy base obtenido: " + adapterBase.toString());
                                
                                // Try ping first
                                System.out.println("      Haciendo ping...");
                                adapterBase.ice_ping();
                                System.out.println("      ✓ Ping exitoso");
                                
                                serverProxy = ServerServicePrx.checkedCast(adapterBase.ice_timeout(3000));
                                if (serverProxy != null) {
                                    System.out.println("    ✓ Conectado via adaptador: " + adapterId);
                                    
                                    // Test the connection
                                    CandidateData[] candidates = serverProxy.getCandidates();
                                    System.out.println("    ✓ Verificación completada. Candidatos: " + candidates.length);
                                    break;
                                } else {
                                    System.out.println("      ✗ CheckedCast devolvió null");
                                }
                            } else {
                                System.out.println("      ✗ No se pudo obtener proxy base");
                            }
                        } catch (com.zeroc.Ice.NotRegisteredException e) {
                            System.out.println("    ✗ Adaptador " + adapterId + " no está registrado en IceGrid");
                        } catch (com.zeroc.Ice.ConnectTimeoutException e) {
                            System.out.println("    ✗ Timeout con adaptador " + adapterId);
                        } catch (com.zeroc.Ice.ConnectionRefusedException e) {
                            System.out.println("    ✗ Conexión rechazada por adaptador " + adapterId);
                        } catch (com.zeroc.Ice.NoEndpointException e) {
                            System.out.println("    ✗ No hay endpoints para adaptador " + adapterId);
                        } catch (Exception e) {
                            System.out.println("    ✗ Error con adaptador " + adapterId + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Error durante la búsqueda de servidores: " + e.getMessage());
                e.printStackTrace();
            }
            
            if (serverProxy == null) {
                throw new RuntimeException("No se pudo conectar a ningún ServerService");
            }

            System.out.println("Iniciando Proxy Cache Server...");
            ObjectAdapter adapter = communicator.createObjectAdapter("ProxyCacheAdapter");            
            proxyCacheService = new ProxyCacheService(serverProxy, communicator);
            adapter.add(proxyCacheService, Util.stringToIdentity("ProxyCache"));
            adapter.activate();

            System.out.println("✓ Proxy Cache Server iniciado y esperando consultas en puerto 40650");
            
            communicator.waitForShutdown();
            
        } catch (Exception e) {
            System.err.println("Error en Proxy Cache: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (proxyCacheService != null) {
                proxyCacheService.shutdown();
            }
            System.out.println("Proxy Cache terminado");
        }
    }
}