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
package handlers.effecthandlers;

import org.l2j.gameserver.engine.skill.api.Skill;
import org.l2j.gameserver.engine.skill.api.SkillEffectFactory;
import org.l2j.gameserver.model.StatsSet;
import org.l2j.gameserver.model.actor.Creature;
import org.l2j.gameserver.model.actor.instance.Player;
import org.l2j.gameserver.model.effects.AbstractEffect;
import org.l2j.gameserver.model.item.instance.Item;
import org.l2j.gameserver.network.serverpackets.SkillCoolTime;

import static java.util.Objects.nonNull;

/**
 * @author Mobius
 * @author JoeAlisson
 */
public class ReuseSkillById extends AbstractEffect {
	private final int skillId;
	
	private ReuseSkillById(StatsSet params)
	{
		skillId = params.getInt("id", 0);
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}
	
	@Override
	public void instant(Creature effector, Creature effected, Skill skill, Item item) {
		final Player player = effector.getActingPlayer();
		if (nonNull(player)) {
			final Skill s = player.getKnownSkill(skillId);
			if (nonNull(s)) {
				player.removeTimeStamp(s);
				player.enableSkill(s);
				player.sendPacket(new SkillCoolTime(player));
			}
		}
	}

	public static class Factory implements SkillEffectFactory {

		@Override
		public AbstractEffect create(StatsSet data) {
			return new ReuseSkillById(data);
		}

		@Override
		public String effectName() {
			return "reuse-skill";
		}
	}
}
