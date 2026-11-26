package avilaos.cluster;

import avilaos.enums.TipoOperacionES;

/** Una solicitud atendida durante una comparación: primer bloque, operación y nombre del proceso que la pidió. */
public record Atencion(int bloque, TipoOperacionES operacion, String proceso) {
}
