package avilaos.pruebas;

import avilaos.cluster.CargaSimulacion;
import avilaos.cluster.Cluster;
import avilaos.cluster.ComparadorPoliticas;
import avilaos.cluster.Computador;
import avilaos.cluster.ResultadoPolitica;
import avilaos.config.ArchivoProcesos;
import avilaos.config.ArchivoSistemaArchivos;
import avilaos.config.DatosProcesos;
import avilaos.config.DatosSistemaArchivos;
import avilaos.enums.EstadoProceso;
import avilaos.enums.Permiso;
import avilaos.enums.PoliticaDiscoTipo;
import avilaos.enums.TipoOperacionES;
import avilaos.estructuras.ListaEnlazada;
import avilaos.estructuras.Nodo;
import avilaos.log.Debug;
import avilaos.log.LogEventos;
import avilaos.so.PCB;
import avilaos.so.archivos.InfoEntrada;
import avilaos.so.archivos.SistemaArchivos;
import avilaos.so.archivos.Usuario;
import avilaos.vista.gui.AccionArchivo;
import avilaos.vista.gui.DialogoCarga;
import avilaos.vista.gui.DialogoOperacionArchivo;
import avilaos.vista.gui.FotoArchivos;
import avilaos.vista.gui.OpcionesArchivo;
import avilaos.vista.gui.PanelMetricasDisco;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pruebas de terminal de la etapa 19: robustez ante entradas dañadas y al azar, y el efecto de la suspensión. */
public final class PruebasPaso19 {

    public static final String RUTA_SUSPENSION = "config/procesos_suspension.json";

    private static final String RUTA_FS = DialogoCarga.FS_ENUNCIADO;
    private static final String RUTA_PROCESOS = DialogoCarga.PROCESOS_ENUNCIADO;
    private static final int MUTANTES = 160;
    private static final int TOPE_CICLOS = 6000;
    private static final int FORMULARIOS = 3000;
    private static final int OPERACIONES = 160;
    private static final int CARGAS = 8;
    private static final String[] NUMEROS = {"-1", "0", "1", "7", "250", "99999999", "2147483648", "1.5", "\"diez\"", "null", "true"};
    private static final String[] TEXTOS = {"\"\"", "\"/\"", "\"/usuarios\"", "\"/no/existe.txt\"", "\"READ\"", "\"DELETE\"",
        "\"CREATE\"", "\"DIRECTORY\"", "\"FILE\"", "\"private\"", "\"user9\"", "\"SCAN\"", "\"RR\"", "null", "7",
        "\"" + "x".repeat(60) + "\""};
    private static final String[] VALORES = {"", " ", "0", "-5", "1", "16", "abc", "99999999999", "2147483647", "1024", "1025",
        "3.5", " 12 ", "10000", "10001", "1000000", "1000001", "48"};
    private static final String[] NOMBRES = {"", "  ", "a", "nuevo.txt", "con/barra", "..", ".", "x".repeat(41), "ok_1",
        "dato csv", " espacio "};
    private static final Usuario[] USUARIOS = {Usuario.ADMIN, Usuario.comun("user1"), Usuario.comun("user2"),
        Usuario.comun("user3")};
    private static final String[] DUENOS = {Usuario.NOMBRE_ADMIN, "user1", "user2"};

    private static Resultados r;
    private static LogEventos log;

    private PruebasPaso19() {
    }

    /** Ejecuta todas las pruebas de la etapa 19 e imprime su resumen. */
    public static void ejecutar(LogEventos logEventos) {
        log = logEventos;
        r = new Resultados(19, "Etapa 19: robustez y efecto de la suspensión");
        probarJsonMutados();
        probarFormularioAlAzar();
        probarPermisosAlAzar();
        probarJsonDeSuspension();
        probarSuspensionEnVivo();
        probarPoliticasConCargasAlAzar();
        Debug.setCiclo(0);
        r.cerrar();
    }

    // ---------------------------------------------------------------- JSON mutados

    /** Cientos de JSON dañados al azar: ninguno lanza una excepción y los que se arman corren íntegros hasta terminar. */
    private static void probarJsonMutados() {
        r.grupo("JSON mutados al azar, de punta a punta");
        String fsBase = leer(RUTA_FS);
        String procesosBase = leer(RUTA_PROCESOS);
        Azar azar = new Azar(19);
        int excepciones = 0;
        int incargables = 0;
        int armados = 0;
        int completos = 0;
        int rotos = 0;
        int conObservaciones = 0;
        String primerProblema = "";
        boolean antes = Debug.isSegundoPlano();
        boolean salida = Debug.isActivo();
        long erroresAntes = Debug.getErrores();
        Debug.setSegundoPlano(true);
        Debug.setActivo(false);
        try {
            for (int i = 0; i < MUTANTES; i++) {
                boolean mutaFs = i % 2 == 0 || azar.entre(0, 3) == 0;
                String fs = mutaFs ? mutar(fsBase, azar, azar.entre(1, 3)) : fsBase;
                String procesos = !mutaFs || azar.entre(0, 3) == 0 ? mutar(procesosBase, azar, azar.entre(1, 3)) : procesosBase;
                Cluster c = null;
                try {
                    DatosSistemaArchivos datosFs = ArchivoSistemaArchivos.interpretar(fs);
                    DatosProcesos datosProcesos = ArchivoProcesos.interpretar(procesos);
                    if (!datosFs.cargable() || !datosProcesos.cargable()) {
                        incargables++;
                        continue;
                    }
                    conObservaciones += datosFs.problemas().estaVacia() && datosProcesos.problemas().estaVacia() ? 0 : 1;
                    CargaSimulacion carga = new CargaSimulacion();
                    carga.usarSistemaArchivos(datosFs);
                    carga.usarProcesos(datosProcesos);
                    CargaSimulacion.Resultado resultado = carga.armarConProcesos(log);
                    if (!resultado.armado()) {
                        incargables++;
                        continue;
                    }
                    armados++;
                    c = resultado.cluster();
                    String problema = correrVerificando(c, TOPE_CICLOS);
                    completos += c.todosTerminados() ? 1 : 0;
                    if (problema != null) {
                        rotos++;
                        primerProblema = primerProblema.isEmpty() ? "mutante " + i + ": " + problema : primerProblema;
                    }
                } catch (RuntimeException e) {
                    excepciones++;
                    primerProblema = primerProblema.isEmpty() ? "mutante " + i + ": " + e : primerProblema;
                } finally {
                    if (c != null) {
                        c.apagar();
                    }
                }
            }
        } finally {
            Debug.setActivo(salida);
            Debug.setSegundoPlano(antes);
        }
        r.verificar("los JSON que no se pueden leer dejan sus ERROR contados (la salida se silencia durante el bombardeo)",
                incargables > 0 && Debug.getErrores() - erroresAntes >= incargables, (Debug.getErrores() - erroresAntes) + " ERROR para "
                + incargables + " incargables");
        r.verificar("ningún JSON dañado lanza una excepción", excepciones == 0, excepciones + " excepciones; " + primerProblema);
        r.verificar("hay de los tres tipos: incargables, cargables con observaciones y armados", incargables > 10
                && conObservaciones > 10 && armados > 30, incargables + " incargables, " + conObservaciones
                + " con observaciones, " + armados + " armados");
        r.verificar("RAM, disco y árbol íntegros en todo el recorrido de los armados", rotos == 0, rotos + "; " + primerProblema);
        r.verificar("todos los armados terminan sus procesos", completos == armados, completos + " de " + armados);
        r.seccion(" JSON mutados: " + MUTANTES + " pares, " + incargables + " incargables, " + armados + " armados y "
                + completos + " terminados");
    }

    /** Aplica cambios al azar: números, textos, líneas borradas o repetidas, un carácter menos o el archivo cortado. */
    private static String mutar(String texto, Azar azar, int veces) {
        String t = texto;
        for (int k = 0; k < veces && !t.isEmpty(); k++) {
            int tipo = azar.entre(0, 11);
            if (tipo <= 3) {
                t = reemplazarToken(t, azar, false, NUMEROS[azar.entre(0, NUMEROS.length - 1)]);
            } else if (tipo <= 6) {
                t = reemplazarToken(t, azar, true, TEXTOS[azar.entre(0, TEXTOS.length - 1)]);
            } else if (tipo == 7) {
                t = cambiarLinea(t, azar, false);
            } else if (tipo == 8) {
                t = cambiarLinea(t, azar, true);
            } else if (tipo == 9) {
                int p = azar.entre(0, t.length() - 1);
                t = t.substring(0, p) + t.substring(p + 1);
            } else if (tipo == 10) {
                t = t.substring(0, azar.entre(0, t.length() - 1));
            } else {
                t = t.replace("\"FIFO\"", "\"" + PoliticaDiscoTipo.values()[azar.entre(0, 5)].getNombre() + "\"");
            }
        }
        return t;
    }

    /** Reemplaza un número (o una cadena entre comillas) elegido al azar por el valor indicado. */
    private static String reemplazarToken(String texto, Azar azar, boolean cadena, String valor) {
        int total = contarTokens(texto, cadena, -1);
        if (total == 0) {
            return texto;
        }
        int elegido = azar.entre(0, total - 1);
        int inicio = contarTokens(texto, cadena, elegido);
        int fin = finDeToken(texto, inicio, cadena);
        return texto.substring(0, inicio) + valor + texto.substring(fin);
    }

    /** Cuenta los tokens del tipo pedido; si buscado no es -1, devuelve la posición donde empieza ese token. */
    private static int contarTokens(String texto, boolean cadena, int buscado) {
        int cuenta = 0;
        int i = 0;
        while (i < texto.length()) {
            char ch = texto.charAt(i);
            boolean empieza = cadena ? ch == '"' : (Character.isDigit(ch) || ch == '-');
            if (empieza) {
                if (cuenta == buscado) {
                    return i;
                }
                cuenta++;
                i = finDeToken(texto, i, cadena);
            } else {
                i++;
            }
        }
        return cuenta;
    }

    /** Posición siguiente al final del número o de la cadena que empieza en inicio. */
    private static int finDeToken(String texto, int inicio, boolean cadena) {
        int i = inicio + 1;
        if (cadena) {
            while (i < texto.length() && texto.charAt(i) != '"') {
                i += texto.charAt(i) == '\\' ? 2 : 1;
            }
            return Math.min(i + 1, texto.length());
        }
        while (i < texto.length() && "0123456789.eE+-".indexOf(texto.charAt(i)) >= 0) {
            i++;
        }
        return i;
    }

    /** Borra o repite una línea al azar (en estos JSON, cada entrada ocupa una línea). */
    private static String cambiarLinea(String texto, Azar azar, boolean repetir) {
        String[] lineas = texto.split("\n", -1);
        int k = azar.entre(0, lineas.length - 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.length; i++) {
            if (i != k || repetir) {
                sb.append(lineas[i]).append('\n');
            }
            if (i == k && repetir) {
                sb.append(lineas[i]).append('\n');
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- formulario

    /** Miles de combinaciones de entradas en el formulario de E/S: nunca falla y lo aceptado siempre está en rango. */
    private static void probarFormularioAlAzar() {
        r.grupo("Formulario de procesos de E/S con entradas al azar");
        InfoEntrada[] entradas = FotoArchivos.de(armado().getComputador(0).getSistemaArchivos()).entradas();
        Azar azar = new Azar(7);
        AccionArchivo[] acciones = AccionArchivo.values();
        int[] rams = {16, 48, 1024};
        int[] libres = {0, 1, 192};
        int excepciones = 0;
        int aceptados = 0;
        int fueraDeRango = 0;
        int sinMotivo = 0;
        String ejemplo = "";
        for (int i = 0; i < FORMULARIOS; i++) {
            AccionArchivo accion = acciones[azar.entre(0, acciones.length - 1)];
            InfoEntrada entrada = entradas[azar.entre(0, entradas.length - 1)];
            String memoria = VALORES[azar.entre(0, VALORES.length - 1)];
            String deadline = VALORES[azar.entre(0, VALORES.length - 1)];
            String instrucciones = VALORES[azar.entre(0, VALORES.length - 1)];
            String bloques = VALORES[azar.entre(0, VALORES.length - 1)];
            String nombre = NOMBRES[azar.entre(0, NOMBRES.length - 1)];
            String nuevo = NOMBRES[azar.entre(0, NOMBRES.length - 1)];
            int ram = rams[azar.entre(0, 2)];
            int libre = libres[azar.entre(0, 2)];
            try {
                DialogoOperacionArchivo.Armado a = DialogoOperacionArchivo.armar(accion, entrada, nombre, memoria, deadline,
                        instrucciones, azar.entre(1, 10), nuevo, bloques, ram, libre);
                if (!a.valido()) {
                    sinMotivo += a.motivo() == null || a.motivo().isBlank() ? 1 : 0;
                    continue;
                }
                aceptados++;
                String problema = fueraDeRango(a, accion, ram, libre);
                if (problema != null) {
                    fueraDeRango++;
                    ejemplo = ejemplo.isEmpty() ? problema : ejemplo;
                }
            } catch (RuntimeException e) {
                excepciones++;
                ejemplo = ejemplo.isEmpty() ? e.toString() : ejemplo;
            }
        }
        r.verificar("ninguna combinación lanza una excepción", excepciones == 0, excepciones + "; " + ejemplo);
        r.verificar("todo rechazo trae su motivo", sinMotivo == 0, sinMotivo + "");
        r.verificar("lo aceptado siempre está en rango (memoria, deadline, instrucciones, bloques y nombre)", fueraDeRango == 0,
                fueraDeRango + "; " + ejemplo);
        r.verificar("hubo combinaciones aceptadas y rechazadas", aceptados > 20 && aceptados < FORMULARIOS,
                aceptados + " de " + FORMULARIOS);
        r.seccion(" Formulario: " + FORMULARIOS + " combinaciones, " + aceptados + " aceptadas");
    }

    /** Motivo por el que un formulario aceptado tiene datos fuera de rango, o null. */
    private static String fueraDeRango(DialogoOperacionArchivo.Armado a, AccionArchivo accion, int ram, int libres) {
        if (a.datos().memoria() < 1 || a.datos().memoria() > ram) {
            return "memoria " + a.datos().memoria() + " con RAM " + ram;
        }
        if (a.datos().deadline() < 1 || a.datos().deadline() > DialogoOperacionArchivo.MAX_DEADLINE) {
            return "deadline " + a.datos().deadline();
        }
        if (a.datos().instrucciones() < 1 || a.datos().instrucciones() > DialogoOperacionArchivo.MAX_INSTRUCCIONES) {
            return "instrucciones " + a.datos().instrucciones();
        }
        if (accion == AccionArchivo.CREAR_ARCHIVO && (a.datos().bloquesArchivo() < 1 || a.datos().bloquesArchivo() > libres)) {
            return "bloques " + a.datos().bloquesArchivo() + " con " + libres + " libres";
        }
        if (a.datos().nombre().isBlank()) {
            return "nombre de proceso vacío";
        }
        if (accion.creaAlgo() || accion == AccionArchivo.RENOMBRAR) {
            String ruta = a.datos().ruta();
            String nombre = accion == AccionArchivo.RENOMBRAR ? a.datos().nuevoNombre() : ruta.substring(ruta.lastIndexOf('/') + 1);
            if (SistemaArchivos.motivoNombre(nombre) != null) {
                return "nombre nuevo inválido '" + nombre + "'";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- permisos

    /** Operaciones al azar de usuarios al azar: lo que el menú habilita se cumple y lo que deshabilita no cambia nada. */
    private static void probarPermisosAlAzar() {
        r.grupo("Permisos al azar: el menú y el kernel coinciden");
        long errores = Debug.getErrores();
        Cluster c = sinProcesos(PoliticaDiscoTipo.C_LOOK);
        SistemaArchivos fs = c.getComputador(0).getSistemaArchivos();
        Azar azar = new Azar(23);
        int habilitadas = 0;
        int denegadas = 0;
        int incumplidas = 0;
        int cambiosIndebidos = 0;
        int duenosMal = 0;
        int rotos = 0;
        String ejemplo = "";
        for (int i = 0; i < OPERACIONES; i++) {
            Usuario usuario = USUARIOS[azar.entre(0, USUARIOS.length - 1)];
            InfoEntrada[] entradas = FotoArchivos.de(fs).entradas();
            InfoEntrada entrada = entradas[azar.entre(0, entradas.length - 1)];
            OpcionesArchivo.Opcion[] opciones = OpcionesArchivo.para(entrada, usuario, fs);
            OpcionesArchivo.Opcion opcion = opciones[azar.entre(0, opciones.length - 1)];
            AccionArchivo accion = opcion.accion();
            String nuevo = "n" + i + (accion == AccionArchivo.CREAR_ARCHIVO ? ".txt" : "");
            Usuario actuante = accion.creaAlgo()
                    ? DialogoOperacionArchivo.actuante(usuario, DUENOS[azar.entre(0, DUENOS.length - 1)]) : usuario;
            DialogoOperacionArchivo.Armado armado = DialogoOperacionArchivo.armar(accion, entrada, "op" + i, "16", "50000", "2", 5,
                    nuevo, String.valueOf(azar.entre(1, 3)), 1024, c.getComputador(0).getDisco().contarLibres(false));
            if (!armado.valido()) {
                continue;
            }
            String antes = huella(fs);
            PCB p = c.crearProcesoArchivo(armado.datos(), actuante, azar.entre(0, 1) == 0 ? Permiso.PUBLICO : Permiso.PRIVADO);
            hasta(c, p, 600);
            boolean cumplida = p != null && p.getEstado() == EstadoProceso.TERMINADO && p.getMotivoRechazo() == null;
            String descripcion = usuario.nombre() + " " + accion + " " + entrada.ruta();
            if (opcion.habilitada()) {
                habilitadas++;
                if (!cumplida) {
                    incumplidas++;
                    ejemplo = ejemplo.isEmpty() ? descripcion + " habilitada y rechazada: " + (p == null ? "null" : p.getMotivoRechazo()) : ejemplo;
                } else if (accion.creaAlgo()) {
                    InfoEntrada creada = buscar(fs, armado.datos().ruta());
                    if (creada == null || !creada.dueno().equals(actuante.nombre())) {
                        duenosMal++;
                        ejemplo = ejemplo.isEmpty() ? descripcion + ": dueño " + (creada == null ? "?" : creada.dueno()) : ejemplo;
                    }
                }
            } else {
                denegadas++;
                if (cumplida) {
                    incumplidas++;
                    ejemplo = ejemplo.isEmpty() ? descripcion + " deshabilitada y cumplida" : ejemplo;
                }
                if (!antes.equals(huella(fs))) {
                    cambiosIndebidos++;
                    ejemplo = ejemplo.isEmpty() ? descripcion + " deshabilitada y cambió el árbol" : ejemplo;
                }
            }
            if (!c.getComputador(0).getDisco().verificarIntegridad() || !fs.verificarIntegridad()) {
                rotos++;
            }
        }
        r.verificar("lo habilitado se cumple y lo deshabilitado termina con su motivo", incumplidas == 0, incumplidas + "; " + ejemplo);
        r.verificar("lo deshabilitado no cambia el árbol ni el disco", cambiosIndebidos == 0, cambiosIndebidos + "; " + ejemplo);
        r.verificar("lo creado queda a nombre de quien actúa (o del dueño que eligió el administrador)", duenosMal == 0,
                duenosMal + "; " + ejemplo);
        r.verificar("disco y árbol íntegros después de cada operación", rotos == 0, rotos + "");
        r.verificar("hubo muchas operaciones de cada tipo", habilitadas > 30 && denegadas > 30, habilitadas + " habilitadas, "
                + denegadas + " deshabilitadas");
        r.verificar("los rechazos por permiso no son ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Permisos: " + habilitadas + " operaciones permitidas y " + denegadas + " negadas, todas coherentes");
        c.apagar();
    }

    /** Ruta, tipo, dueño, permiso y bloques de cada entrada: cambia si el árbol o el disco cambian. */
    private static String huella(SistemaArchivos fs) {
        StringBuilder sb = new StringBuilder();
        Nodo<InfoEntrada> nodo = fs.listarTodo().getCabeza();
        while (nodo != null) {
            InfoEntrada e = nodo.getDato();
            sb.append(e.ruta()).append('|').append(e.tipo()).append('|').append(e.dueno()).append('|').append(e.permiso())
                    .append('|').append(e.primerBloque()).append('|').append(e.bloques()).append(';');
            nodo = nodo.getSiguiente();
        }
        return sb.toString();
    }

    private static InfoEntrada buscar(SistemaArchivos fs, String ruta) {
        Nodo<InfoEntrada> nodo = fs.listarTodo().getCabeza();
        while (nodo != null) {
            if (nodo.getDato().ruta().equals(ruta)) {
                return nodo.getDato();
            }
            nodo = nodo.getSiguiente();
        }
        return null;
    }

    // ---------------------------------------------------------------- suspensión

    /** El JSON de suspensión (RAM 48) contra el mismo con RAM 1024, con las seis políticas de disco. */
    private static void probarJsonDeSuspension() {
        r.grupo("JSON de suspensión: RAM 48 contra RAM 1024");
        long errores = Debug.getErrores();
        DatosProcesos datos = ArchivoProcesos.leer(RUTA_SUSPENSION);
        r.verificar("se carga sin observaciones: RAM 48, 8 procesos de 16 y prioridades distintas", datos.cargable()
                && datos.problemas().estaVacia() && datos.ram() == 48 && datos.procesos().tamano() == 8
                && prioridadesDistintas(datos), "");
        ComparadorPoliticas.Comparacion poca = ComparadorPoliticas.comparar(carga(datos), null);
        ComparadorPoliticas.Comparacion mucha = ComparadorPoliticas.comparar(carga(ArchivoProcesos.interpretar(
                leer(RUTA_SUSPENSION).replace("\"ramCapacity\": 48", "\"ramCapacity\": 1024"))), null);
        r.verificar("las 12 corridas se completan sin vencidos ni rechazos", poca.hecha() && mucha.hecha()
                && todasLimpias(poca) && todasLimpias(mucha), poca.motivo() + " / " + mucha.motivo());
        if (!poca.hecha() || !mucha.hecha()) {
            return;
        }
        boolean sinSuspensionConMucha = true;
        for (ResultadoPolitica p : mucha.resultados()) {
            sinSuspensionConMucha &= p.metricas().suspensiones() == 0;
        }
        r.verificar("con RAM 1024 nadie se suspende", sinSuspensionConMucha, "");
        r.verificar("con RAM 48, FIFO y SSTF no completan ninguna suspensión (la E/S termina antes que la escritura al swap)",
                suspensiones(poca, PoliticaDiscoTipo.FIFO) == 0 && suspensiones(poca, PoliticaDiscoTipo.SSTF) == 0,
                suspensiones(poca, PoliticaDiscoTipo.FIFO) + " / " + suspensiones(poca, PoliticaDiscoTipo.SSTF));
        boolean barridos = true;
        for (PoliticaDiscoTipo t : new PoliticaDiscoTipo[]{PoliticaDiscoTipo.SCAN, PoliticaDiscoTipo.C_SCAN,
            PoliticaDiscoTipo.LOOK, PoliticaDiscoTipo.C_LOOK}) {
            barridos &= suspensiones(poca, t) == 3 && poca.de(t).metricas().reactivaciones() == 3;
        }
        r.verificar("con RAM 48, las cuatro de barrido suspenden y reactivan a 3 procesos", barridos, "");
        ResultadoPolitica clookPoca = poca.de(PoliticaDiscoTipo.C_LOOK);
        ResultadoPolitica clookMucha = mucha.de(PoliticaDiscoTipo.C_LOOK);
        r.verificar("con suspensión, C-LOOK mueve más el cabezal, espera más y termina más tarde",
                clookPoca.metricas().desplazamientoTotal() > clookMucha.metricas().desplazamientoTotal()
                && clookPoca.metricas().esperaPromedioTotal() > clookMucha.metricas().esperaPromedioTotal()
                && clookPoca.cicloFinal() > clookMucha.cicloFinal(), "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Política: desplazamiento / espera / suspensiones / ciclo final, con RAM 1024 -> con RAM 48");
        for (PoliticaDiscoTipo t : PoliticaDiscoTipo.values()) {
            ResultadoPolitica a = mucha.de(t);
            ResultadoPolitica b = poca.de(t);
            r.seccion("   " + t.getNombre() + ": " + a.metricas().desplazamientoTotal() + " / "
                    + PanelMetricasDisco.decimal(a.metricas().esperaPromedioTotal()) + " / " + a.metricas().suspensiones()
                    + " / " + a.cicloFinal() + " -> " + b.metricas().desplazamientoTotal() + " / "
                    + PanelMetricasDisco.decimal(b.metricas().esperaPromedioTotal()) + " / " + b.metricas().suspensiones()
                    + " / " + b.cicloFinal());
        }
    }

    /** El JSON de suspensión corrido en vivo: con SCAN hay procesos en el swap; con FIFO la suspensión se pide y se cancela. */
    private static void probarSuspensionEnVivo() {
        r.grupo("Suspensión en vivo con el JSON de suspensión");
        long errores = Debug.getErrores();
        Cluster scan = desdeSuspension(PoliticaDiscoTipo.SCAN);
        Computador pc = scan.getComputador(0);
        boolean vistoEnSwap = false;
        boolean vistoListoSuspendido = false;
        boolean vistoBloqueadoSuspendido = false;
        boolean ramSiempreBien = true;
        int ciclos = 0;
        while (!(ciclos > 0 && scan.todosTerminados()) && ciclos++ < TOPE_CICLOS) {
            scan.getReloj().ejecutarCiclos(1);
            ramSiempreBien &= pc.getRam().getUsada() <= 48 && pc.getRam().verificarIntegridad();
            Nodo<PCB> nodo = scan.todosLosProcesos().getCabeza();
            while (nodo != null) {
                PCB p = nodo.getDato();
                vistoEnSwap |= p.getBloqueSwap() >= pc.getDisco().getBloquesArchivos();
                vistoListoSuspendido |= p.getEstado() == EstadoProceso.LISTO_SUSPENDIDO;
                vistoBloqueadoSuspendido |= p.getEstado() == EstadoProceso.BLOQUEADO_SUSPENDIDO;
                nodo = nodo.getSiguiente();
            }
        }
        r.verificar("SCAN: hubo procesos con su imagen en el área de swap (bloque 200 o más)", vistoEnSwap, "");
        r.verificar("SCAN: se vieron las colas Listo/Suspendido y Bloqueado/Suspendido", vistoListoSuspendido
                && vistoBloqueadoSuspendido, vistoListoSuspendido + " / " + vistoBloqueadoSuspendido);
        r.verificar("SCAN: la RAM nunca pasa de 48 y siempre cuadra", ramSiempreBien, "");
        r.verificar("SCAN: terminan los 8, sin vencidos, con el swap vacío y el disco íntegro", scan.todosTerminados()
                && vencidos(scan) == 0 && pc.getDisco().contarLibres(true) == 100 && pc.getDisco().verificarIntegridad()
                && pc.getSistemaArchivos().verificarIntegridad(), vencidos(scan) + " vencidos, swap libre "
                + pc.getDisco().contarLibres(true));
        r.verificar("SCAN: 3 suspensiones y 3 reactivaciones", pc.getKernel().getSuspensiones() == 3
                && pc.getKernel().getReactivaciones() == 3, pc.getKernel().getSuspensiones() + " / " + pc.getKernel().getReactivaciones());
        scan.apagar();

        Cluster fifo = desdeSuspension(PoliticaDiscoTipo.FIFO);
        boolean pedida = false;
        ciclos = 0;
        while (!(ciclos > 0 && fifo.todosTerminados()) && ciclos++ < TOPE_CICLOS) {
            fifo.getReloj().ejecutarCiclos(1);
            Nodo<PCB> nodo = fifo.todosLosProcesos().getCabeza();
            while (nodo != null) {
                pedida |= nodo.getDato().getIntercambio() == TipoOperacionES.SUSPENDER;
                nodo = nodo.getSiguiente();
            }
        }
        r.verificar("FIFO: la suspensión se pide pero se cancela porque la E/S del proceso termina antes", pedida
                && fifo.getComputador(0).getKernel().getSuspensiones() == 0 && fifo.todosTerminados()
                && fifo.getComputador(0).getDisco().contarLibres(true) == 100, "pedida " + pedida + ", suspensiones "
                + fifo.getComputador(0).getKernel().getSuspensiones());
        fifo.apagar();
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
    }

    // ---------------------------------------------------------------- políticas

    /** Varias cargas al azar comparadas con las seis políticas: promedios de desplazamiento y espera. */
    private static void probarPoliticasConCargasAlAzar() {
        r.grupo("Las seis políticas con cargas al azar");
        long errores = Debug.getErrores();
        PoliticaDiscoTipo[] tipos = PoliticaDiscoTipo.values();
        long[] desplazamiento = new long[tipos.length];
        double[] espera = new double[tipos.length];
        int incompletas = 0;
        String fsBase = leer(RUTA_FS);
        String[] rutas = {"/sistema/boot.bin", "/usuarios/notas.txt", "/sistema/script.py", "/sistema/core.dll",
            "/sistema/config.sys", "/usuarios/img1.png", "/usuarios/data.csv", "/usuarios/video.mp4"};
        for (int semilla = 1; semilla <= CARGAS; semilla++) {
            Azar azar = new Azar(100 + semilla);
            StringBuilder procesos = new StringBuilder("{\"globalConfig\":{\"cycleDurationMs\":0,\"swapSizeBlocks\":100,"
                    + "\"initialDiskPolicy\":\"FIFO\"},\"computer\":{\"ramCapacity\":1024,\"policy\":\"FCFS\",\"quantum\":0},"
                    + "\"processes\":[");
            int cantidad = azar.entre(10, 18);
            for (int i = 0; i < cantidad; i++) {
                procesos.append(i == 0 ? "" : ",").append("{\"name\":\"P").append(i).append("\",\"memory\":16,\"deadline\":20000,")
                        .append("\"instructions\":").append(azar.entre(1, 8)).append(",\"targetPath\":\"")
                        .append(rutas[azar.entre(0, rutas.length - 1)]).append("\",\"operation\":\"")
                        .append(azar.entre(0, 2) == 0 ? "UPDATE" : "READ").append("\",\"fileSizeBlocks\":0,\"priority\":")
                        .append(azar.entre(1, 9)).append('}');
            }
            procesos.append("]}");
            CargaSimulacion carga = new CargaSimulacion();
            carga.usarSistemaArchivos(ArchivoSistemaArchivos.interpretar(fsBase.replace("\"initialHead\": 50",
                    "\"initialHead\": " + azar.entre(0, 199))));
            carga.usarProcesos(ArchivoProcesos.interpretar(procesos.toString()));
            ComparadorPoliticas.Comparacion comp = ComparadorPoliticas.comparar(carga, null);
            if (!comp.hecha() || !todasLimpias(comp)) {
                incompletas++;
                continue;
            }
            for (int t = 0; t < tipos.length; t++) {
                desplazamiento[t] += comp.de(tipos[t]).metricas().desplazamientoTotal();
                espera[t] += comp.de(tipos[t]).metricas().esperaPromedioTotal();
            }
        }
        r.verificar("las " + CARGAS * tipos.length + " corridas terminan sin vencidos ni rechazos", incompletas == 0, incompletas + "");
        int fifo = PoliticaDiscoTipo.FIFO.ordinal();
        r.verificar("en promedio, SSTF, LOOK y C-LOOK mueven el cabezal menos que FIFO",
                desplazamiento[PoliticaDiscoTipo.SSTF.ordinal()] < desplazamiento[fifo]
                && desplazamiento[PoliticaDiscoTipo.LOOK.ordinal()] < desplazamiento[fifo]
                && desplazamiento[PoliticaDiscoTipo.C_LOOK.ordinal()] < desplazamiento[fifo], "");
        r.verificar("LOOK mueve menos que SCAN y C-LOOK menos que C-SCAN (no van hasta el borde)",
                desplazamiento[PoliticaDiscoTipo.LOOK.ordinal()] < desplazamiento[PoliticaDiscoTipo.SCAN.ordinal()]
                && desplazamiento[PoliticaDiscoTipo.C_LOOK.ordinal()] < desplazamiento[PoliticaDiscoTipo.C_SCAN.ordinal()], "");
        r.verificar("SSTF espera en promedio menos que FIFO", espera[PoliticaDiscoTipo.SSTF.ordinal()] < espera[fifo], "");
        r.verificar("cero ERROR", Debug.getErrores() == errores, (Debug.getErrores() - errores) + "");
        r.seccion(" Promedio de " + CARGAS + " cargas al azar: desplazamiento / espera promedio");
        for (int t = 0; t < tipos.length; t++) {
            r.seccion("   " + tipos[t].getNombre() + ": " + (desplazamiento[t] / CARGAS) + " / "
                    + PanelMetricasDisco.decimal(espera[t] / CARGAS));
        }
    }

    // ---------------------------------------------------------------- ayudantes

    /** Corre hasta que todos terminan (o el tope), revisando cada 25 ciclos la RAM, el disco y el árbol. */
    private static String correrVerificando(Cluster c, int tope) {
        Computador pc = c.getComputador(0);
        int ciclos = 0;
        while (!(ciclos > 0 && c.todosTerminados()) && ciclos < tope) {
            c.getReloj().ejecutarCiclos(25);
            ciclos += 25;
            String problema = integridad(pc);
            if (problema != null) {
                return "ciclo " + c.getReloj().getCiclo() + ": " + problema;
            }
        }
        return integridad(pc);
    }

    private static String integridad(Computador pc) {
        if (!pc.getRam().verificarIntegridad()) {
            return "RAM";
        }
        if (!pc.getDisco().verificarIntegridad()) {
            return "disco";
        }
        return pc.getSistemaArchivos().verificarIntegridad() ? null : "árbol";
    }

    /** Corre ciclos hasta que el proceso termina (o el tope). */
    private static void hasta(Cluster c, PCB p, int tope) {
        int ciclos = 0;
        while (p != null && p.getEstado() != EstadoProceso.TERMINADO && ciclos++ < tope) {
            c.getReloj().ejecutarCiclos(1);
        }
    }

    private static Cluster armado() {
        CargaSimulacion carga = new CargaSimulacion();
        carga.cargarSistemaArchivos(RUTA_FS);
        carga.cargarProcesos(RUTA_PROCESOS);
        return carga.armarConProcesos(log).cluster();
    }

    /** Árbol del enunciado sin procesos, con el cabezal rápido y sin espera anticipatoria. */
    private static Cluster sinProcesos(PoliticaDiscoTipo politica) {
        String procesos = "{\"globalConfig\":{\"cycleDurationMs\":0,\"swapSizeBlocks\":100,\"initialDiskPolicy\":\""
                + politica.getNombre() + "\",\"headSpeed\":10,\"anticipationCycles\":0},"
                + "\"computer\":{\"ramCapacity\":1024,\"policy\":\"FCFS\",\"quantum\":0},\"processes\":[]}";
        CargaSimulacion carga = new CargaSimulacion();
        carga.cargarSistemaArchivos(RUTA_FS);
        carga.usarProcesos(ArchivoProcesos.interpretar(procesos));
        return carga.armar(log).cluster();
    }

    /** El JSON de suspensión armado con la política de disco indicada y sus 8 procesos. */
    private static Cluster desdeSuspension(PoliticaDiscoTipo politica) {
        DatosProcesos datos = ArchivoProcesos.interpretar(leer(RUTA_SUSPENSION)
                .replace("\"FIFO\"", "\"" + politica.getNombre() + "\""));
        return carga(datos).armarConProcesos(log).cluster();
    }

    private static CargaSimulacion carga(DatosProcesos procesos) {
        CargaSimulacion carga = new CargaSimulacion();
        carga.cargarSistemaArchivos(RUTA_FS);
        carga.usarProcesos(procesos);
        return carga;
    }

    private static boolean todasLimpias(ComparadorPoliticas.Comparacion comp) {
        for (ResultadoPolitica p : comp.resultados()) {
            if (!p.completo() || p.vencidos() > 0 || p.rechazados() > 0) {
                return false;
            }
        }
        return comp.resultados().length == PoliticaDiscoTipo.values().length;
    }

    private static long suspensiones(ComparadorPoliticas.Comparacion comp, PoliticaDiscoTipo tipo) {
        return comp.de(tipo).metricas().suspensiones();
    }

    private static boolean prioridadesDistintas(DatosProcesos datos) {
        boolean[] vistas = new boolean[11];
        Nodo<avilaos.so.DatosProcesoArchivo> nodo = datos.procesos().getCabeza();
        while (nodo != null) {
            int p = nodo.getDato().prioridad();
            if (p < 1 || p > 10 || vistas[p]) {
                return false;
            }
            vistas[p] = true;
            nodo = nodo.getSiguiente();
        }
        return true;
    }

    private static int vencidos(Cluster c) {
        int vencidos = 0;
        ListaEnlazada<PCB> todos = c.todosLosProcesos();
        Nodo<PCB> nodo = todos.getCabeza();
        while (nodo != null) {
            vencidos += nodo.getDato().isDeadlineVencido() ? 1 : 0;
            nodo = nodo.getSiguiente();
        }
        return vencidos;
    }

    private static String leer(String ruta) {
        try {
            return Files.readString(Path.of(ruta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Debug.error("Pruebas", "no se pudo leer " + ruta + ": " + e.getMessage());
            return "";
        }
    }

    /** Generador reproducible (xorshift). */
    private static final class Azar {

        private long estado;

        Azar(long semilla) {
            estado = semilla * 0x9E3779B97F4A7C15L + 1;
        }

        /** Entero uniforme entre min y max, ambos incluidos. */
        int entre(int min, int max) {
            estado ^= estado << 13;
            estado ^= estado >>> 7;
            estado ^= estado << 17;
            return min + (int) Math.floorMod(estado, (long) (max - min + 1));
        }
    }
}
