/*
 * Copyright © 2019-2020 L2JOrg
 *
 * This file is part of the L2JOrg project.
 *
 * L2JOrg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2JOrg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2j.gameserver.network.serverpackets;

import org.l2j.gameserver.Config;
import org.l2j.gameserver.data.database.dao.ItemDAO;
import org.l2j.gameserver.data.database.dao.PlayerDAO;
import org.l2j.gameserver.data.database.dao.PlayerSubclassesDAO;
import org.l2j.gameserver.data.database.data.ItemVariationData;
import org.l2j.gameserver.data.database.data.PlayerData;
import org.l2j.gameserver.data.database.data.PlayerSubclassesData;
import org.l2j.gameserver.data.sql.impl.ClanTable;
import org.l2j.gameserver.data.xml.impl.LevelData;
import org.l2j.gameserver.enums.InventorySlot;
import org.l2j.gameserver.model.CharSelectInfoPackage;
import org.l2j.gameserver.model.Clan;
import org.l2j.gameserver.model.VariationInstance;
import org.l2j.gameserver.model.actor.instance.Player;
import org.l2j.gameserver.model.entity.Hero;
import org.l2j.gameserver.network.Disconnection;
import org.l2j.gameserver.network.GameClient;
import org.l2j.gameserver.network.ServerPacketId;
import org.l2j.gameserver.settings.ServerSettings;
import org.l2j.gameserver.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;

import static org.l2j.commons.configuration.Configurator.getSettings;
import static org.l2j.commons.database.DatabaseAccess.getDAO;
import static org.l2j.gameserver.enums.InventorySlot.RIGHT_HAND;
import static org.l2j.gameserver.enums.InventorySlot.TWO_HAND;

/**
 * @author JoeAlisson
 * @reworked Thoss
 * [Delete direct access to database]
 */
public class CharSelectionInfo extends ServerPacket {

    private static final Logger LOGGER = LoggerFactory.getLogger(CharSelectionInfo.class);
    private static final int CLIENT_ZONE_OFFSET = ZoneOffset.ofHours(5).getTotalSeconds();
    private final String _loginName;
    private final int _sessionId;
    private final CharSelectInfoPackage[] _characterPackages;
    private int _activeId;

    public CharSelectionInfo(String loginName, int sessionId) {
        this(loginName, sessionId, -1);
    }

    public CharSelectionInfo(String loginName, int sessionId, int activeId) {
        _sessionId = sessionId;
        _loginName = loginName;
        _characterPackages = loadCharacterSelectInfo(_loginName);
        _activeId = activeId;
    }

    private static CharSelectInfoPackage[] loadCharacterSelectInfo(String loginName) {
        CharSelectInfoPackage charInfopackage;
        LinkedList<CharSelectInfoPackage> characterList = new LinkedList<>();
        try {
            boolean reloadDatas = false;
            List<PlayerData> charDatas = getDAO(PlayerDAO.class).findAllCharactersByAccount(loginName);

            for(PlayerData charData : charDatas) {
                final Player player = World.getInstance().findPlayer(charData.getCharId());
                if (player != null) {
                    reloadDatas = true;
                    Disconnection.of(player).storeMe().deleteMe();
                }
            }

            if (reloadDatas)
                charDatas = getDAO(PlayerDAO.class).findAllCharactersByAccount(loginName);

            for(PlayerData charData : charDatas) {
                charInfopackage = restoreChar(charData);
                if (charInfopackage != null) {
                    characterList.add(charInfopackage);
                }
            }

            return characterList.toArray(CharSelectInfoPackage[]::new);
        } catch (Exception e) {
            LOGGER.warn("Could not restore char info: " + e.getMessage(), e);
        }

        return new CharSelectInfoPackage[0];
    }

    private static void loadCharacterSubclassInfo(CharSelectInfoPackage charInfopackage, int ObjectId, int activeClassId) {
        PlayerSubclassesData charSubClassesDatas = getDAO(PlayerSubclassesDAO.class).findByIdAndClassId(ObjectId, activeClassId);

        try {
            if (charSubClassesDatas != null) {
                charInfopackage.setExp(charSubClassesDatas.getExp());
                charInfopackage.setSp(charSubClassesDatas.getSp());
                charInfopackage.setLevel(charSubClassesDatas.getLevel());
                charInfopackage.setVitalityPoints(charSubClassesDatas.getVitalityPoints());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not restore char subclass info: " + e.getMessage(), e);
        }
    }

    private static CharSelectInfoPackage restoreChar(PlayerData chardata) throws Exception {
        final int objectId = chardata.getCharId();
        final String name = chardata.getName();

        // See if the char must be deleted
        final long deletetime = chardata.getDeleteTime();
        if (deletetime > 0) {
            if (System.currentTimeMillis() > deletetime) {
                final Clan clan = ClanTable.getInstance().getClan(chardata.getClanId());
                if (clan != null) {
                    clan.removeClanMember(objectId, 0);
                }

                GameClient.deleteCharByObjId(objectId);
                return null;
            }
        }

        final CharSelectInfoPackage charInfopackage = new CharSelectInfoPackage(objectId, name);
        charInfopackage.setAccessLevel(chardata.getAccessLevel());
        charInfopackage.setLevel(chardata.getLevel());
        charInfopackage.setMaxHp((int) chardata.getMaxtHp());
        charInfopackage.setCurrentHp(chardata.getCurrentHp());
        charInfopackage.setMaxMp((int) chardata.getMaxtMp());
        charInfopackage.setCurrentMp(chardata.getCurrentMp());
        charInfopackage.setReputation(chardata.getReputation());
        charInfopackage.setPkKills(chardata.getPk());
        charInfopackage.setPvPKills(chardata.getPvP());
        charInfopackage.setFace(chardata.getFace());
        charInfopackage.setHairStyle(chardata.getHairStyle());
        charInfopackage.setHairColor(chardata.getHairColor());
        charInfopackage.setSex(chardata.isFemale() ? 1 : 0);

        charInfopackage.setExp(chardata.getExp());
        charInfopackage.setSp(chardata.getSp());
        charInfopackage.setVitalityPoints(chardata.getVitalityPoints());
        charInfopackage.setClanId(chardata.getClanId());

        charInfopackage.setRace(chardata.getRace());

        final int baseClassId = chardata.getBaseClass();
        final int activeClassId = chardata.getClassId();

        charInfopackage.setX(chardata.getX());
        charInfopackage.setY(chardata.getY());
        charInfopackage.setZ(chardata.getZ());


        if (Config.MULTILANG_ENABLE) {
            String lang = chardata.getLanguage();
            if (!Config.MULTILANG_ALLOWED.contains(lang)) {
                lang = Config.MULTILANG_DEFAULT;
            }
            charInfopackage.setHtmlPrefix("data/lang/" + lang + "/");
        }

        // if is in subclass, load subclass exp, sp, lvl info
        if (baseClassId != activeClassId) {
            loadCharacterSubclassInfo(charInfopackage, objectId, activeClassId);
        }

        charInfopackage.setClassId(activeClassId);

        // Get the augmentation id for equipped weapon
        int weaponObjId = charInfopackage.getPaperdollObjectId(RIGHT_HAND);
        if (weaponObjId < 1) {
            weaponObjId = charInfopackage.getPaperdollObjectId(TWO_HAND);
        }

        if (weaponObjId > 0) {
            ItemVariationData itemVariation = getDAO(ItemDAO.class).findItemVariationByItemId(weaponObjId);

            try {
                if (itemVariation != null) {
                    int mineralId = itemVariation.getMineralId();
                    int option1 = itemVariation.getOption1();
                    int option2 = itemVariation.getOption2();
                    if ((option1 != -1) && (option2 != -1)) {
                        charInfopackage.setAugmentation(new VariationInstance(mineralId, option1, option2));
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Could not restore augmentation info: " + e.getMessage(), e);
            }
        }

        // Check if the base class is set to zero and also doesn't match with the current active class, otherwise send the base class ID. This prevents chars created before base class was introduced from being displayed incorrectly.
        if ((baseClassId == 0) && (activeClassId > 0)) {
            charInfopackage.setBaseClassId(activeClassId);
        } else {
            charInfopackage.setBaseClassId(baseClassId);
        }

        charInfopackage.setDeleteTimer(deletetime);
        charInfopackage.setLastAccess(chardata.getLastAccess());
        charInfopackage.setNoble(chardata.isNobless());
        return charInfopackage;
    }

    public CharSelectInfoPackage[] getCharInfo() {
        return _characterPackages;
    }

    @Override
    public void writeImpl(GameClient client) {
        writeId(ServerPacketId.CHARACTER_SELECTION_INFO);

        final int size = _characterPackages.length;
        writeInt(size); // Created character count

        writeInt(Config.MAX_CHARACTERS_NUMBER_PER_ACCOUNT); // Can prevent players from creating new characters (if 0); (if 1, the client will ask if chars may be created (0x13) Response: (0x0D) )
        writeByte(size == Config.MAX_CHARACTERS_NUMBER_PER_ACCOUNT); // if 1 can't create new char
        writeByte(0x01); // 0=can't play, 1=can play free until level 85, 2=100% free play
        writeInt(0x02); // if 1, Korean client
        writeByte(0x00); // Gift message for inactive accounts // 152
        writeByte(0x00); // Balthus Knights, if 1 suggests premium account

        long lastAccess = 0;
        if (_activeId == -1) {
            for (int i = 0; i < size; i++) {
                if (lastAccess < _characterPackages[i].getLastAccess()) {
                    lastAccess = _characterPackages[i].getLastAccess();
                    _activeId = i;
                }
            }
        }
        for (int i = 0; i < size; i++) {
            var charInfoPackage = _characterPackages[i];

            writeString(charInfoPackage.getName()); // Character name
            writeInt(charInfoPackage.getObjectId()); // Character ID
            writeString(_loginName); // Account name
            writeInt(_sessionId); // Account ID
            writeInt(0x00); // Pledge ID
            writeInt(0x00); // Builder level

            writeInt(charInfoPackage.getSex()); // Sex
            writeInt(charInfoPackage.getRace()); // Race
            writeInt(charInfoPackage.getClassId());

            writeInt(getSettings(ServerSettings.class).serverId());

            writeInt(charInfoPackage.getX());
            writeInt(charInfoPackage.getY());
            writeInt(charInfoPackage.getZ());
            writeDouble(charInfoPackage.getCurrentHp());
            writeDouble(charInfoPackage.getCurrentMp());

            writeLong(charInfoPackage.getSp());
            writeLong(charInfoPackage.getExp());
            writeDouble((float) (charInfoPackage.getExp() - LevelData.getInstance().getExpForLevel(charInfoPackage.getLevel())) / (LevelData.getInstance().getExpForLevel(charInfoPackage.getLevel() + 1) - LevelData.getInstance().getExpForLevel(charInfoPackage.getLevel()))); // High

            writeInt(charInfoPackage.getLevel());

            writeInt(charInfoPackage.getReputation());
            writeInt(charInfoPackage.getPkKills());
            writeInt(charInfoPackage.getPvPKills());

            writeInt(0x00);
            writeInt(0x00);
            writeInt(0x00);
            writeInt(0x00);
            writeInt(0x00);
            writeInt(0x00);
            writeInt(0x00);

            writeInt(0x00); // Ertheia
            writeInt(0x00); // Ertheia

            for (var slot : getPaperdollOrder()) {
                writeInt(charInfoPackage.getPaperdollItemId(slot.getId()));
            }

            writeInt(0x00); // RHAND Visual ID not Used on Classic
            writeInt(0x00); // LHAND Visual ID not Used on Classic
            writeInt(0x00); // GLOVES Visual ID not Used on Classic
            writeInt(0x00); // CHEST Visual ID not Used on Classic
            writeInt(0x00); // LEGS Visual ID not Used on Classic
            writeInt(0x00); // FEET Visual ID not Used on Classic
            writeInt(0x00); // RHAND Visual ID not Used on Classic
            writeInt(0x00); // HAIR Visual ID not Used on Classic
            writeInt(0x00); // HAIR2 Visual ID not Used on Classic


            writeShort( charInfoPackage.getEnchantEffect(InventorySlot.CHEST.getId())); // Upper Body enchant level
            writeShort( charInfoPackage.getEnchantEffect(InventorySlot.LEGS.getId())); // Lower Body enchant level
            writeShort( charInfoPackage.getEnchantEffect(InventorySlot.HEAD.getId())); // Headgear enchant level
            writeShort( charInfoPackage.getEnchantEffect(InventorySlot.GLOVES.getId())); // Gloves enchant level
            writeShort( charInfoPackage.getEnchantEffect(InventorySlot.FEET.getId())); // Boots enchant level

            writeInt(charInfoPackage.getHairStyle());
            writeInt(charInfoPackage.getHairColor());
            writeInt(charInfoPackage.getFace());

            writeDouble(charInfoPackage.getMaxHp()); // Maximum HP
            writeDouble(charInfoPackage.getMaxMp()); // Maximum MP

            writeInt(charInfoPackage.getDeleteTimer() > 0 ? (int) ((charInfoPackage.getDeleteTimer() - System.currentTimeMillis()) / 1000) : 0);
            writeInt(charInfoPackage.getClassId());
            writeInt(i == _activeId);

            writeByte(Math.min(charInfoPackage.getEnchantEffect(RIGHT_HAND.getId()), 127));
            writeInt(charInfoPackage.getAugmentation() != null ? charInfoPackage.getAugmentation().getOption1Id() : 0);
            writeInt(charInfoPackage.getAugmentation() != null ? charInfoPackage.getAugmentation().getOption2Id() : 0);

            // writeInt(charInfoPackage.getTransformId()); // Used to display Transformations
            writeInt(0x00); // Currently on retail when you are on character select you don't see your transformation.

            writeInt(0x00); // Pet NpcId
            writeInt(0x00); // Pet level
            writeInt(0x00); // Pet Food
            writeInt(0x00); // Pet Food Level
            writeDouble(0x00); // Current pet HP
            writeDouble(0x00); // Current pet MP

            writeInt(charInfoPackage.getVitalityPoints()); // Vitality
            writeInt((int) (Config.RATE_VITALITY_EXP_MULTIPLIER * 100)); // Vitality Percent
            writeInt(charInfoPackage.getVitalityItemsUsed()); // Remaining vitality item uses
            writeInt(charInfoPackage.getAccessLevel() == -100 ? 0x00 : 0x01); // Char is active or not
            writeByte(charInfoPackage.isNoble());
            writeByte(Hero.getInstance().isHero(charInfoPackage.getObjectId()) ? 0x02 : 0x00); // Hero glow
            writeByte(charInfoPackage.isHairAccessoryEnabled()); // Show hair accessory if enabled
            writeInt(0); // ban time in secs
            writeInt((int) (charInfoPackage.getLastAccess() / 1000) + CLIENT_ZONE_OFFSET);

        }
    }
}
