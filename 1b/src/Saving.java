package src;
public class Saving {
    public boolean SaveEntity(){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        //Files.writeString(Path.of("saving\brain.json"), Str.toString());
        for(int i=0; i<Entity_manager.Entity_count();i++){
            int topology[] = Entity_manager.Entity_get(i).getBrain().getTopology();
            for(int j=0; j<topology.length-1;j++){
                for(int z=0;z<topology[j+1];z++){
                    Str.append(Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getBias());
                    Str.append(" -- ");
                    double weights[] = Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getWeight();
                    for(int c=0;c<weights.length;c++){
                        Str.append(weights[c]+" - ");
                    }
                }
                Str.append("\n");
            }
        }
        return flag;
    }
}
