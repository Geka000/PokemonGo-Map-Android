package com.deerengine.pokemongo.api;


public class PokemonInfo {

    private String spawnPointId;
    public int id;
    public String name;

    public long disappear_timestamp;

    public double lat;
    public double lng;

    PokemonInfo(String spawnPointId, int id, String name){
        this.spawnPointId = spawnPointId;
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != PokemonInfo.class){
            return false;
        }
        return hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        String str = spawnPointId+":"+id;
        return str.hashCode();
    }

    @Override
    public String toString() {
        return id+":"+name+"["+lat+","+lng+"]";
    }
}
