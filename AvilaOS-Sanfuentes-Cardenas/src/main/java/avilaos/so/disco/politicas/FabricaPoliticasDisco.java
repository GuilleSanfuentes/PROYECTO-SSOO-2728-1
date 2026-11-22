package avilaos.so.disco.politicas;

import avilaos.enums.PoliticaDiscoTipo;

/** Crea la política de disco correspondiente a cada tipo. */
public final class FabricaPoliticasDisco {

    private FabricaPoliticasDisco() {
    }

    /** Nueva instancia de la política indicada; con null devuelve FIFO. */
    public static PoliticaDisco crear(PoliticaDiscoTipo tipo) {
        if (tipo == null) {
            return new PoliticaDiscoFIFO();
        }
        return switch (tipo) {
            case FIFO -> new PoliticaDiscoFIFO();
            case SSTF -> new PoliticaDiscoSSTF();
            case SCAN -> new PoliticaDiscoSCAN();
            case C_SCAN -> new PoliticaDiscoCSCAN();
            case LOOK -> new PoliticaDiscoLOOK();
            case C_LOOK -> new PoliticaDiscoCLOOK();
        };
    }
}
