package src;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Salvataggi in JSON versionato. Ogni salvataggio e' una cartella saving/s-N con:
 *  - meta.json      : versione formato, seed, tick di simulazione
 *  - world.json     : dimensione, ciclo, livello del terreno e mele (formato del mondo)
 *  - entities.json  : per ogni entita' viva stato, GENOMA completo e cervelli
 *  - genealogy.json : albero genealogico della sessione
 * I cadaveri non vengono salvati (scadono comunque dopo pochi tick).
 */
public class Saving {

    public static final int FORMAT_VERSION = 2;

    private static boolean SaveMeta(Path dir){
        try {
            JSONObject meta = new JSONObject();
            meta.put("formatVersion", FORMAT_VERSION);
            meta.put("seed", Simulation.getSeed());
            meta.put("tick", Simulation.getTick());
            meta.put("genealogySession", Genealogy.getSession());
            Files.writeString(dir.resolve("meta.json"), meta.toString(2));
            return true;
        } catch (Exception e) {System.out.println("Errore Saving meta "+e.getMessage());}
        return false;
    }

    private static boolean SaveEntities(Path dir){
        try {
            JSONArray arr = new JSONArray();
            for(entity e : Entity_manager.snapshotAlive()){
                arr.put(e.toJson());
            }
            JSONObject root = new JSONObject();
            root.put("formatVersion", FORMAT_VERSION);
            root.put("entities", arr);
            Files.writeString(dir.resolve("entities.json"), root.toString(1));
            return true;
        } catch (Exception e) {System.out.println("Errore Saving entities "+e.getMessage());}
        return false;
    }

    private static boolean SaveWorld(Path dir){
        StringBuilder Str = new StringBuilder();

        Str.append("-dimension : ").append(world.getDim()+"\n");
        Str.append("-cycle : ").append(world.getCycle()+"\n");
        Str.append("-ground : ").append(world.getGround()+"\n");
        Str.append("-food : \n");

        int dim = world.getDim();
        for(int x=0; x<dim; x++){
            for(int y=0; y<dim; y++){
                for(int z=0; z<dim; z++){
                    if("M".equals(world.getSymbol(x, y, z))){
                        Str.append(x+","+y+","+z+";\n");
                    }
                }
            }
        }

        try {
            Files.writeString(dir.resolve("world.json"), Str.toString());
            return true;
        } catch (Exception e) {System.out.println("Errore Saving world "+e.getMessage());}
        return false;
    }

    private static boolean SaveGenealogy(Path dir){
        try {
            Genealogy.saveTo(dir.resolve("genealogy.json"));
            return true;
        } catch (Exception e) {System.out.println("Errore Saving genealogy "+e.getMessage());}
        return false;
    }

    public static boolean Save(){
        boolean flag = false;
        try {
            Files.createDirectories(Path.of("saving"));
        } catch (Exception e) {System.out.println("Errore creazione cartella Saving "+e.getMessage());}

        File[] subdirs = new File("saving/").listFiles(File::isDirectory);
        int maxN = -1;
        if(subdirs != null){
            for(File d : subdirs){
                String[] parts = d.getName().split("-");
                try { maxN = Math.max(maxN, Integer.parseInt(parts[1])); } catch(Exception ignored) {}
            }
        }
        Path dir = Path.of("saving/s-" + (maxN + 1));
        try{
            Files.createDirectories(dir);
        }catch(Exception e){System.out.println("Errore creazione cartella "+e.getMessage());}

        // Il lock del ciclo garantisce che non ci sia un tick a meta' mentre si fotografa il mondo
        synchronized (Simulation.LOCK) {
            boolean f1 = SaveMeta(dir);
            boolean f2 = SaveWorld(dir);
            boolean f3 = SaveEntities(dir);
            boolean f4 = SaveGenealogy(dir);
            flag = f1 && f2 && f3 && f4;
            System.out.println("Save meta/world/entities/genealogy: "+f1+" "+f2+" "+f3+" "+f4);
        }
        return flag;
    }

    public static boolean Load(int n){
        boolean flag = false;
        try {
            File[] subdirs = new File("saving/").listFiles(File::isDirectory);
            Path path = subdirs[n].toPath();
            if(!Files.exists(path.resolve("entities.json"))){
                System.err.println("Salvataggio in formato vecchio (manca entities.json): non supportato.");
                return false;
            }

            Simulation.stop();
            synchronized (Simulation.LOCK) {
                Entity_manager.clearAll();
                Simulation.reset();

                JSONObject meta = new JSONObject(Files.readString(path.resolve("meta.json")));
                Simulation.setSeed(meta.optLong("seed", Simulation.getSeed()));
                Simulation.setTick(meta.optLong("tick", 0));

                world.Load(path);

                JSONObject root = new JSONObject(Files.readString(path.resolve("entities.json")));
                JSONArray arr = root.getJSONArray("entities");
                for(int i=0;i<arr.length();i++){
                    Entity_manager.Entity_add(entity.fromJson(arr.getJSONObject(i)));
                }

                if(Files.exists(path.resolve("genealogy.json"))){
                    Genealogy.loadFrom(path.resolve("genealogy.json"));
                }else{
                    Genealogy.newSession();
                }
                StatsLog.open(Genealogy.getSession());
            }
            world.PauseCycle(false);
            world.DoCycle();
            flag = true;
        } catch (Exception e) {
            System.err.println("Errore Load: "+e);
            e.printStackTrace();
        }

        return flag;
    }
}
