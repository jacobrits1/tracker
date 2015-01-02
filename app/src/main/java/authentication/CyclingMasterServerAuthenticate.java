package authentication;


import utils.WebUtils;

public class CyclingMasterServerAuthenticate implements ServerAuthenticate {

    @Override
    public String userSignUp(String name, String email, String pass, String authType) throws Exception {
        String url = "http://cyclingmaster-mobilebackend.rhcloud.com/users";
        String params = "usersignup=true&username" + name + "&email=" + email + "&pass=" + pass + "&authType=" + authType;
        // TODO: Handle sign up result
        WebUtils.executePost(url, params);
        return null;
    }

    @Override
    public String userSignIn(String user, String pass, String authType) throws Exception {
        String url = "http://cyclingmaster-mobilebackend.rhcloud.com/users";
        String params = "userlogin=true&email=" + user + "&pass=" + pass + "&authType=" + authType;
        // TODO: Handle sign in result
        WebUtils.executePost(url, params);
        return null;
    }
}
