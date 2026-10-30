package avilaos.config;

import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.hardware.Reloj;
import avilaos.so.DatosProceso;
import avilaos.sync.DatosBuffer;

/** Arma una configuración nueva a partir de los parámetros del formulario, reutilizando opcionalmente la carga del clúster actual. */
public final class NuevaConfiguracion {

    /** Resultado de armar la configuración: trae la configuración o el motivo del rechazo. */
    public record Resultado(ConfiguracionSistema config, String motivo) {

        /** Indica si la configuración se pudo armar. */
        public boolean valido() {
            return config != null;
        }
    }

    private NuevaConfiguracion() {
    }

    /** Valida los parámetros y la carga heredada; el resto (parada, envejecimiento, debugger) se toma de la configuración actual. */
    public static Resultado construir(ConfiguracionSistema actual, int duracionCicloMs, int latenciaRed,
            ListaEnlazada<ConfigComputador> computadores, boolean incluirCarga) {
        String error = validarParametros(duracionCicloMs, latenciaRed, computadores);
        if (error != null) {
            return new Resultado(null, error);
        }
        ListaEnlazada<DatosBuffer> buffers = new ListaEnlazada<>();
        ListaEnlazada<DatosProceso> procesos = new ListaEnlazada<>();
        if (incluirCarga) {
            error = validarCarga(actual, computadores);
            if (error != null) {
                return new Resultado(null, error);
            }
            copiar(actual.getBuffers(), buffers);
            copiar(actual.getProcesos(), procesos);
        }
        ConfiguracionSistema config = new ConfiguracionSistema(actual.isDebugger(), duracionCicloMs, actual.getParada(),
                latenciaRed, actual.getEnvejecimientoCadaCiclos(), computadores, buffers, procesos, 0);
        return new Resultado(config, null);
    }

    private static String validarParametros(int duracionCicloMs, int latenciaRed, ListaEnlazada<ConfigComputador> computadores) {
        if (duracionCicloMs < 0 || duracionCicloMs > Reloj.DURACION_MAXIMA_MS) {
            return "Duración del ciclo: " + duracionCicloMs + " está fuera del rango 0.." + Reloj.DURACION_MAXIMA_MS;
        }
        if (latenciaRed < 0 || latenciaRed > ConfiguracionSistema.MAX_LATENCIA) {
            return "Latencia de red: " + latenciaRed + " está fuera del rango 0.." + ConfiguracionSistema.MAX_LATENCIA;
        }
        int n = computadores == null ? 0 : computadores.tamano();
        if (n < ConfiguracionSistema.MIN_COMPUTADORES || n > ConfiguracionSistema.MAX_COMPUTADORES) {
            return "Número de computadores: " + n + " está fuera del rango " + ConfiguracionSistema.MIN_COMPUTADORES
                    + ".." + ConfiguracionSistema.MAX_COMPUTADORES;
        }
        int numero = 0;
        Nodo<ConfigComputador> nodo = computadores.getCabeza();
        while (nodo != null) {
            ConfigComputador c = nodo.getDato();
            if (c.ram() < 1 || c.ram() > ConfiguracionSistema.MAX_RAM) {
                return "Computador " + numero + ": RAM " + c.ram() + " está fuera del rango 1.." + ConfiguracionSistema.MAX_RAM;
            }
            if (c.politica() == null) {
                return "Computador " + numero + ": falta la política";
            }
            if (c.quantum() < 1 || c.quantum() > ConfiguracionSistema.MAX_QUANTUM) {
                return "Computador " + numero + ": quantum " + c.quantum() + " está fuera del rango 1.." + ConfiguracionSistema.MAX_QUANTUM;
            }
            numero++;
            nodo = nodo.getSiguiente();
        }
        return null;
    }

    /** Devuelve el primer problema de la carga heredada (con cuántos más hay) o null si todo cabe en los computadores nuevos. */
    private static String validarCarga(ConfiguracionSistema actual, ListaEnlazada<ConfigComputador> computadores) {
        int n = computadores.tamano();
        int[] ram = new int[n];
        int[] ocupado = new int[n];
        int mayorRam = 0;
        int i = 0;
        Nodo<ConfigComputador> c = computadores.getCabeza();
        while (c != null) {
            ram[i] = c.getDato().ram();
            mayorRam = Math.max(mayorRam, ram[i]);
            i++;
            c = c.getSiguiente();
        }
        String primero = null;
        int total = 0;
        Nodo<DatosBuffer> b = actual.getBuffers().getCabeza();
        while (b != null) {
            DatosBuffer d = b.getDato();
            String problema = null;
            if (d.computador() < 0 || d.computador() >= n) {
                problema = "el buffer '" + d.nombre() + "' está en el computador " + d.computador() + ", que no existe en la nueva configuración (0.." + (n - 1) + ")";
            } else if (ocupado[d.computador()] + d.capacidad() > ram[d.computador()]) {
                problema = "el buffer '" + d.nombre() + "' (capacidad " + d.capacidad() + ") no cabe en la RAM del Computador "
                        + d.computador() + ": quedan " + (ram[d.computador()] - ocupado[d.computador()]) + " libres de " + ram[d.computador()];
            } else {
                ocupado[d.computador()] += d.capacidad();
            }
            if (problema != null) {
                total++;
                primero = primero == null ? problema : primero;
            }
            b = b.getSiguiente();
        }
        Nodo<DatosProceso> p = actual.getProcesos().getCabeza();
        while (p != null) {
            DatosProceso d = p.getDato();
            String problema = null;
            if (d.computador() >= n) {
                problema = "el proceso '" + d.nombre() + "' está en el computador " + d.computador() + ", que no existe en la nueva configuración (0.." + (n - 1) + " o -1)";
            } else if (d.computador() >= 0 && d.memoria() > ram[d.computador()]) {
                problema = "el proceso '" + d.nombre() + "' pide " + d.memoria() + " de memoria y la RAM del Computador "
                        + d.computador() + " es " + ram[d.computador()];
            } else if (d.computador() < 0 && d.memoria() > mayorRam) {
                problema = "el proceso '" + d.nombre() + "' pide " + d.memoria() + " de memoria y la mayor RAM de la nueva configuración es " + mayorRam;
            }
            if (problema != null) {
                total++;
                primero = primero == null ? problema : primero;
            }
            p = p.getSiguiente();
        }
        if (primero == null) {
            return null;
        }
        String resto = total > 1 ? " (y " + (total - 1) + (total == 2 ? " problema más)" : " problemas más)") : "";
        return "No se guardó: " + primero + resto;
    }

    private static <T> void copiar(ListaEnlazada<T> origen, ListaEnlazada<T> destino) {
        Nodo<T> nodo = origen.getCabeza();
        while (nodo != null) {
            destino.agregarFinal(nodo.getDato());
            nodo = nodo.getSiguiente();
        }
    }
}
