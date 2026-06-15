package com.hiddengemstore.uitls;

import com.hiddengemstore.entity.dto.UserDTO;

/**
 * 用户持有器
 * @author : ZhaoJH
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> TL = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        TL.set(user);
    }

    public static UserDTO getUser(){
        return TL.get();
    }

    public static void removeUser(){
        TL.remove();
    }
}
