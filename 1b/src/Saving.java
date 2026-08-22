package src;
import java.io.File;
import java.nio.file.*;


//Riscrivi tutti i parser

public class Saving {
    private static boolean SaveEntityBrain(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int i=0; i<Entity_manager.Entity_count(); i++){
            entity ent = Entity_manager.Entity_get(i);
            int[] topology = ent.getBrain().getTopology();
            Str.append("id : "+ent.getId()+";\n");
            Str.append("topology : ");
            for(int c:ent.getBrain().getTopology()){
                Str.append(c+",");
            }
            Str.delete(Str.length()-1, Str.length());
            Str.append(";\n");
            
            StringBuilder biasStr = new StringBuilder();
            StringBuilder weightStr = new StringBuilder();
            biasStr.append("bias: ");
            weightStr.append("weights: ");
            for(int j=1;j<topology.length ;j++){
                for(int o=0;o<topology[j];o++){
                    biasStr.append(ent.getBrain().getLayer(j-1).getNeuron(o).getBias()+",");
                    double[] weigh = ent.getBrain().getLayer(j-1).getNeuron(o).getWeight();
                    for(int k=0;k<weigh.length;k++){
                        weightStr.append(weigh[k]+",");
                    }
                }
            }
            biasStr.deleteCharAt(biasStr.length()-1);
            biasStr.append(";\n");
            weightStr.deleteCharAt(weightStr.length()-1);
            weightStr.append(";\n");
            Str.append(biasStr);
            Str.append(weightStr);
            Str.append("\n");
            Str.append("-\n");
        }
        


        try {
            Files.writeString(Path.of(dir+"/brain.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain "+e.getMessage());}
        return flag;
    }

    private static boolean SaveEntity(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int v=0; v<Entity_manager.Entity_count();v++){
            Str.append("{ ");
            Str.append("id"+" : "+Entity_manager.Entity_get(v).getId()+",");
            Str.append(" age"+" : "+Entity_manager.Entity_get(v).getAge()+",");
            Str.append(" food"+" : "+Entity_manager.Entity_get(v).getFood()+",");
            Str.append(" foodConsumed"+" : "+Entity_manager.Entity_get(v).getFoodConsumed()+",");
            Str.append(" healt"+" : "+Entity_manager.Entity_get(v).getHealt()+",");
            Str.append(" sex"+" : "+Entity_manager.Entity_get(v).getSex()+",");
            Str.append(" fitness"+" : "+ Entity_manager.Entity_get(v).getFitness()+",");
            int[] tempos = Entity_manager.Entity_get(v).getPos();
            Str.append(" pos"+" : "+tempos[0]+", "+tempos[1]+", "+tempos[2]);
            Str.append("},\n");
        }
        try {
            Files.writeString(Path.of(dir+"/entity.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain"+e.getMessage());}
        return flag;
    }

    private static boolean SaveWorld(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();

        Str.append("  -dimension : ").append(world.getDim()+"\n");
        Str.append("  -cycle : ").append(world.getCycle()+"\n");
        Str.append("  -ground : ").append(world.getGround()+"\n");
        Str.append("  -food : \n");

        java.util.List<int[]> foodCells = new java.util.ArrayList<>();
        int dim = world.getDim();
        for(int x=0; x<dim; x++){
            for(int y=0; y<dim; y++){
                for(int z=0; z<dim; z++){
                    if("M".equals(world.getSymbol(x, y, z))){
                        foodCells.add(new int[]{x, y, z});
                    }
                }
            }
        }

        for(int i=0; i<foodCells.size(); i++){
            int[] pos = foodCells.get(i);
            Str.append(" "+pos[0]+" ,"+pos[1]+","+pos[2]+";\n");
        }

        try {
            Files.writeString(Path.of(dir+"/world.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving world "+e.getMessage());}
        return flag;
    }

    public static boolean Save(){
        boolean flag = false;
        File root = new File(".");
        File[] subdirs = root.listFiles(File::isDirectory);
        boolean flag2 = false;
        for(File e: subdirs){
            if(e.getName().equals("saving")){
                flag2 = true;
            }
        }
        try {
            if(!flag2){Files.createDirectories(Path.of(root+"/saving"));}
        } catch (Exception e) {System.out.println("Errore creazione cartella Saving "+e.getMessage());}

        root = new File("saving/");
        subdirs = root.listFiles(File::isDirectory);

        StringBuilder name = new StringBuilder();
        //ordinamento copiato
        int maxN = -1;
        for(File d : subdirs){
            String[] parts = d.getName().split("-");
            try { maxN = Math.max(maxN, Integer.parseInt(parts[1])); } catch(Exception ignored) {}
        }
        name.append("s-").append(maxN + 1);

        String[] str = name.toString().split("-");
        int n = Integer.parseInt(str[1]);
        name.replace(name.length()-str[1].length(), name.length(), Integer.toString(n));
        try{
            Files.createDirectories(Path.of("saving/" + name.toString()));
        }catch(Exception e){System.out.println("Errore creazione cartella "+e.getMessage());}
        String dir = "saving/"+name.toString();
        try {
            boolean f1 = false;
            boolean f2 = false;
            boolean f3 = false;

            try {
                if(SaveWorld(dir)){f1 = true;}
            } catch (Exception e) {System.out.println("Errore SaveWorld in Save "+e.getMessage());}
            try {
                if(SaveEntity(dir)){f2 = true;}
            } catch (Exception e) {System.out.println("Errore SaveEntity in Save "+e.getMessage());}
            try {
                if(SaveEntityBrain(dir)){f3 = true;}
            } catch (Exception e) {System.out.println("Errore SaveEntityBrain in Save "+e.getMessage());}

            System.out.println("Save1: "+f1);
            System.out.println("Save2: "+f2);
            System.out.println("Save3: "+f3);
        } catch (Exception e) { System.out.println("Errore ultimo if Save: "+e.getMessage());}
        
        return flag;
    }
}
