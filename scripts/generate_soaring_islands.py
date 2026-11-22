"""Moog's Soaring Structures as a third, rarer exploration layer, using its own pools, pieces and loot."""
import json
from pathlib import Path
from generate_content import write

# name: (start pool, jigsaw size, tint biome, cache theme or None when the island already has its own chests, weight)
SOARING = {
    'large_tower': ('large_tower/start_pool', 5, 'plains', None, 3),
    'white_house': ('white_house/start_pool', 5, 'plains', None, 3),
    'diorite_house': ('diorite_house/start_pool', 5, 'plains', None, 3),
    'jungle': ('jungle/start_pool', 5, 'jungle', None, 3),
    'volcano': ('volcano/start_pool', 5, 'savanna', None, 3),
    'red_sand': ('red_sand/start_pool', 5, 'badlands', None, 3),
    'muddy_water_hole': ('muddy_water_hole/start_pool', 5, 'swamp', None, 2),
    'castle_tower': ('castle_tower/start_pool', 2, 'plains', None, 3),
    'castle_ruin': ('castle_ruin_start_pool', 1, 'plains', None, 3),
    'desert_pyramid': ('desert_pyramid/start_pool', 2, 'desert', None, 2),
    'leaf_hollow': ('leaf_hollow/start_pool', 2, 'forest', None, 3),
    'mangrove': ('mangrove/start_pool', 2, 'mangrove_swamp', None, 2),
    'taiga': ('taiga/start_pool', 2, 'taiga', None, 3),
    'spruce_huts': ('spruce_huts_start_pool', 2, 'taiga', None, 2),
    'calcite_house': ('calcite_house_start_pool', 1, 'meadow', None, 2),
    'small_oak_house': ('small_oak_house_start_pool', 1, 'forest', None, 2),
    'small_deepslate_house': ('small_deepslate_house_start_pool', 1, 'plains', None, 2),
    'small_tower': ('small_tower_start_pool', 1, 'plains', None, 2),
    'mushroom': ('mushroom_start_pool', 1, 'mushroom_fields', None, 2),
    'nether_portal': ('nether_portal_start_pool', 1, 'plains', None, 2),
    'palm_island': ('palm_island_start_pool', 2, 'beach', None, 2),
    'birch_river': ('birch_river_start_pool', 2, 'birch_forest', 'birch_forest', 2),
    'cherry_river': ('cherry_river_start_pool', 2, 'cherry_grove', 'cherry_grove', 2),
    'frozen_pond': ('frozen_pond_start_pool', 2, 'snowy_plains', 'ice_spikes', 2),
    'desert_well': ('desert_well_start_pool', 1, 'desert', 'desert', 2),
    'small_pond': ('small_pond_start_pool', 1, 'plains', 'sunflower_plains', 1),
    **{f'tree_{i}': (f'tree_{i}_start_pool', 2, 'forest', theme, 1)
       for i, theme in zip(range(1, 9), ['taiga', 'birch_forest', 'dark_forest', 'jungle', 'savanna', 'lush_caves', 'cherry_grove', 'mangrove_swamp'])},
}


def main():
    res = Path('src/main/resources')
    entries = []
    for name, (pool, size, biome, theme, weight) in SOARING.items():
        write(f'data/farskies/worldgen/structure/soaring_{name}.json', {
            'type': 'farskies:island', 'biomes': ['farskies:open_sky'], 'step': 'top_layer_modification',
            'spawn_overrides': {}, 'pool': 'mss:' + pool, 'island_biome': 'minecraft:' + biome,
            'loot': 'theme/' + theme if theme else 'none', 'size': size, 'terrain_adaptation': 'none'})
        entries.append({'structure': 'farskies:soaring_' + name, 'weight': weight})
    # Rarer than the small biome islands; never overlapping the Ember's landmark grid.
    write('data/farskies/worldgen/structure_set/soaring.json', {'structures': entries, 'placement': {
        'type': 'minecraft:random_spread', 'spacing': 10, 'separation': 4, 'salt': 40917733, 'spread_type': 'linear',
        'exclusion_zone': {'other_set': 'farskies:landmarks', 'chunk_count': 6}}})
    write('data/farskies/tags/worldgen/structure/soaring.json', {'values': [e['structure'] for e in entries]})
    # Small biome islands give way to a nearby large island instead of clipping into it.
    path = res / 'data/farskies/worldgen/structure_set/islands.json'
    data = json.loads(path.read_text())
    data['placement']['exclusion_zone'] = {'other_set': 'farskies:soaring', 'chunk_count': 4}
    write(path.relative_to(res), data)
    path = res / 'data/farskies/tags/worldgen/structure/islands.json'
    data = json.loads(path.read_text())
    data['values'] = [v for v in data['values'] if not v.startswith('farskies:soaring_')] + [e['structure'] for e in entries]
    write(path.relative_to(res), data)
    path = res / 'data/farskies/worldgen/world_preset/far_skies.json'
    data = json.loads(path.read_text())
    settings = data['dimensions']['minecraft:overworld']['generator']['settings']
    settings['structure_overrides'] = ['farskies:islands', 'farskies:landmarks', 'farskies:soaring']
    write(path.relative_to(res), data)
    print(f'Generated {len(entries)} soaring islands.')


if __name__ == '__main__':
    main()
