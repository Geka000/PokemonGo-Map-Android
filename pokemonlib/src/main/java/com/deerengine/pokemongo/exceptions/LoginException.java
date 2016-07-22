package com.deerengine.pokemongo.exceptions;

public class LoginException extends PokemonException {
    public LoginException(){
        super("can't login");
    }
    public LoginException(String error){
        super(error);
    }
}
