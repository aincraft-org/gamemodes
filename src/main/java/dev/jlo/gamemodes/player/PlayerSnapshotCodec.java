package dev.jlo.gamemodes.player;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class PlayerSnapshotCodec {
    private static final int VERSION = 1;
    private PlayerSnapshotCodec() {}
    public static byte[] encode(PlayerSnapshot s) {
        List<String> f=List.of(s.getPlayerId().toString(),joinItems(s.getInventory()),s.getCursor()==null?"":item(s.getCursor()),joinItems(s.getArmor()),s.getOffhand()==null?"":item(s.getOffhand()),joinEffects(s.getEffects()),joinAttributes(s.getAttributes()),Double.toString(s.getHealth()),Integer.toString(s.getFood()),Double.toString(s.getSaturation()),Float.toString(s.getExperience()),Integer.toString(s.getLevel()),esc(s.getGameMode()),Boolean.toString(s.getAllowFlight()),Boolean.toString(s.getFlying()),s.getReturnLocation()==null?"":location(s.getReturnLocation()));
        return (VERSION+"|"+String.join("|",f.stream().map(PlayerSnapshotCodec::esc).toList())).getBytes(StandardCharsets.UTF_8);
    }
    public static PlayerSnapshot decode(byte[] bytes) {
        List<String> p=split(new String(bytes,StandardCharsets.UTF_8),'|'); require(p.size()==17&&p.get(0).equals(Integer.toString(VERSION)),"Unsupported player snapshot");
        Map<String,Double> attrs=new HashMap<>(); if(!p.get(7).isEmpty()) for(String x:p.get(7).split(";")){String[] a=x.split("=",2);attrs.put(unesc(a[0]),Double.parseDouble(a[1]));}
        LocationSnapshot loc=p.get(16).isEmpty()?null:parseLocation(p.get(16));
        return new PlayerSnapshot(UUID.fromString(p.get(1)),parseItems(p.get(2)),p.get(3).isEmpty()?null:parseItem(p.get(3)),parseItems(p.get(4)),p.get(5).isEmpty()?null:parseItem(p.get(5)),p.get(6).isEmpty()?List.of():Arrays.stream(p.get(6).split(";",-1)).map(PlayerSnapshotCodec::parseEffect).toList(),attrs,Double.parseDouble(p.get(8)),Integer.parseInt(p.get(9)),Double.parseDouble(p.get(10)),Float.parseFloat(p.get(11)),Integer.parseInt(p.get(12)),unesc(p.get(13)),Boolean.parseBoolean(p.get(14)),Boolean.parseBoolean(p.get(15)),loc);
    }
    private static ItemStackSnapshot parseItem(String s){List<String> p=split(s,',');require(p.size()==3,"Invalid item");return new ItemStackSnapshot(unesc(p.get(0)),Integer.parseInt(p.get(1)),unesc(p.get(2)));}
    private static List<ItemStackSnapshot> parseItems(String s){return s.isEmpty()?List.of():Arrays.stream(s.split(";",-1)).map(PlayerSnapshotCodec::parseItem).toList();}
    private static String item(ItemStackSnapshot i){return String.join(",",List.of(esc(i.getMaterial()),esc(Integer.toString(i.getAmount())),esc(i.getMetadata())));}
    private static String joinItems(List<ItemStackSnapshot> x){return String.join(";",x.stream().map(PlayerSnapshotCodec::item).toList());}
    private static EffectSnapshot parseEffect(String s){List<String> p=split(s,',');require(p.size()==6,"Invalid effect");return new EffectSnapshot(unesc(p.get(0)),Integer.parseInt(p.get(1)),Integer.parseInt(p.get(2)),Boolean.parseBoolean(p.get(3)),Boolean.parseBoolean(p.get(4)),Boolean.parseBoolean(p.get(5)));}
    private static String effect(EffectSnapshot e){return String.join(",",List.of(esc(e.getType()),Integer.toString(e.getAmplifier()),Integer.toString(e.getDurationTicks()),Boolean.toString(e.getAmbient()),Boolean.toString(e.getParticles()),Boolean.toString(e.getIcon())));}
    private static String joinEffects(List<EffectSnapshot> x){return String.join(";",x.stream().map(PlayerSnapshotCodec::effect).toList());}
    private static String joinAttributes(Map<String,Double> a){return a.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->esc(e.getKey())+"="+e.getValue()).reduce((x,y)->x+";"+y).orElse("");}
    private static String location(LocationSnapshot l){return String.join(",",List.of(esc(l.getWorld()),Double.toString(l.getX()),Double.toString(l.getY()),Double.toString(l.getZ()),Float.toString(l.getYaw()),Float.toString(l.getPitch())));}
    private static LocationSnapshot parseLocation(String s){List<String> p=split(s,',');require(p.size()==6,"Invalid location");return new LocationSnapshot(unesc(p.get(0)),Double.parseDouble(p.get(1)),Double.parseDouble(p.get(2)),Double.parseDouble(p.get(3)),Float.parseFloat(p.get(4)),Float.parseFloat(p.get(5)));}
    private static String esc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);} private static String unesc(String s){return URLDecoder.decode(s,StandardCharsets.UTF_8);}
    private static List<String> split(String s,char d){return Arrays.stream(s.split(java.util.regex.Pattern.quote(Character.toString(d)),-1)).map(PlayerSnapshotCodec::unesc).toList();}
    private static void require(boolean b,String m){if(!b)throw new IllegalArgumentException(m);}
}
