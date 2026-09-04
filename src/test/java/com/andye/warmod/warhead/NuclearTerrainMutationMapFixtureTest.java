package com.andye.warmod.warhead;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Coordinate-level frozen plans for the 62a89 terrain oracle. The compressed
 * constants contain every block mutation, category/guard bit, fire mutation,
 * and biome quart—not a summary fingerprint.
 */
final class NuclearTerrainMutationMapFixtureTest {
    private static final Vec3 CENTER = new Vec3(8.0, 64.0, 8.0);
    private static final ChunkPos CRATER_CHUNK = new ChunkPos(0, 0);
    private static final ChunkPos AFTERMATH_CHUNK = new ChunkPos(4, 0);
    private static final Map<String, String> REFERENCE_62A89 = Map.of(
        "crater_rock", "eJzt3Qe4JGteF+AKX6gvTaEiIiAGVBAVEyqIigEwogQxIQqS88LeDeyuCwZUMCMmBIxgwIAYQAFBFDMqoCLmiIJiwIiC63vu9L3Ts9fp0oG53Afq/zzvnt7+7amurq4Oc86e75fe/OO+ZpqmPD07r/e10/SGP+fyX950mt76Gy+Xf940vdtPuFz+ztP07p98ufydpumlX3q5/BYuf/nl8nedHp6VheQGdl/vMeg0KvvsegadRmX3jfcYdBqV3UbvMeg0KntwPYNOo1LYo4xBp1Ep7EnGoNOoFHYH6R6DTqNS2DcZg06jUtiLjEGnUSns7vQ9Bp1GpbA7KPcYdBqVwu6g3WPQaVQKu4N6j0GnUSnsDvo9Bp1GZfeA3GPQaVT213E9g06jsn871zPoNCr7t3c9g06jUti/g4xBp1Ep7K8rY9BpVAr7d5Qx6DQqhf31ZAw6jUphd2LeY9BpVAr768sYdBqVwu7kvseg06gU9jeQMeg0KoX9DWUMOo1KYX8jGYNOo1LYv4uMQadRKexvLGPQaVQKuyfYPQadRmX/bq5n0GlU9u/uegadRqWwfw8Zg06jUtjfRMag06gU9u8pY9BpVAr795Ix6DQqhf17yxh0GpXC7kXnHoNOo1LY30zGoNOoFPbvI2PQaVQK+5vLGHQalcL+fWUMOo1KYf9+MgadRqWwf38Zg06jUti9EN5j0GlUCvsPkDHoNCqF/QfKGHQalf0HuZ5Bp1Ep7D9YxqDTqBT2HyJj0GlUCvtbyhh0GpXC/kNlDDqNSmH/YTIGnUalsP9wGYNOo1LY30rGoNOoFPa3ljHoNCqF/UfIGHQalcL+NjIGnUalsP9IGYNOo1LYf5SMQadRKew/Wsag06gU9reVMeg0KoX9x8gYdBqV/ce6nkGnUSnsP07GoNOoFPYfL2PQaVQK+9vJGHQalcL+9jIGnUalsL+DjEGnUSns3tzvMeg0KoX9J8oYdBqVwv6TZAw6jUph/8kyBp1GpbD/FBmDTqNS2H+qjEGnUSns7yhj0GlUCvtPkzHoNCqF/afLGHQalcL+TjIGnUalsL+zjEGnUSns7yJj0GlUCvu7yhh0GpXC/jNkDDqNSmF/NxmDTqNS2H+mjEGnUSnsP0vGoNOoFPafLWPQaVQKuw979xh0GpXC/nNlDDqNSmF/dxmDTqNS2H1YvMeg06gU9veQMeg0KoX958sYdBqVwv4LZAw6jUphf08Zg06jUtjfS8ag06gU9l8oY9BpVAr7e8sYdBqVwv4+MgadRqWwv6+MQadRKezvJ2PQaVQK+/vLGHQalcL+ATIGnUalsH+gjEGnUSnsHyRj0GlUCvsHyxh0GpXC/iEyBp1GpbB/qIxBp1Ep7B8mY9BpVAr7h8sYdBqVwv4iGYNOo1LYP0LGoNOoFPaPlDHoNCqF/cUyBp1GpbA/JWPQaVQK+0tkDDqNSmF/qYxBp1Ep7C+TMeg0KoX95TIGnUalsH+UjEGnUSnsr5Ax6DQqhf2VMgadRqWwv0rGoNOoFPZfJGPQaVQK+6tlDDqNSmH/aBmDTqNS2D9GxqDTqBT2Xyxj0GlUCvsvkTHoNCqF/ZfKGHQalcL+y2QMOo1KYf9YGYNOo1LYf7mMQadRKey/Qsag06gU9l8pY9BpVAob+8fJGXQalcL+8TIGnUalsP8qGYNOo1LYf7WMQadRKey/Rsag06gU9l8rY9BpVAr7r5Mx6DQqhf3Xyxh0GpXC/htkDDqNSmH/BBmDTqNS2H+jjEGnUSnsnyhj0GlUCvtvkjHoNCqF/TfLGHQalcL+W2QMOo1KYf+tMgadRqWw/zYZg06jUtg/Scag06gU9t8uY9BpVAr7J8sYdBqVwv4pMgadRqWwf6qMQadRKey/Q8ag06gU9t8pY9BpVAr775Ix6DQqhf13yxh0GpXC/ntkDDqNSmH/vTIGnUalsH+ajEGnUSnsny5j0GlUCvvvkzHoNCqF/ffLGHQalcL+B2QMOo1KYf+DMgadRqWwf4aMQadRKex/SMag06gU9j8sY9BpVAr7H5Ex6DQqhf2Pyhh0GpXC/pkyBp1GpbD/MRmDTqNS2D9LxqDTqBT2Py5j0GlUCvufkDHoNCqF/U/KGHQalcL+p2QMOo1KYf9sGYNOo1LYP0fGoNOoFPY/LWPQaVQK+5+RMeg0KoX9c2UMOo1KYf88GYNOo1LYP1/GoNOoFPY/K2PQaVQK+xfIGHQalcL+hTIGnUalsP85GYNOo1LYv0jGoNOoFPY/L2PQaVQK+1+QMeg0KoX9i2UMOo1KYf+LMgadRqWw/yUZg06jUtj/soxBp1Ep7H9FxqDTqBT2vypj0GlUCvtfkzHoNCqF/a/LGHQalcL+JTIGnUalsP8NGYNOo1LY/6aMQadRKex/S8ag06gU9i+VMeg0KoX9y2QMOo1KYf9yGYNOo1LY/7aMQadRKex/R8ag06gU9r8rY9BpVAr7V8gYdBqVwv73ZAw6jUph/0oZg06jUtj/voxBp1Ep7P9AxqDTqBT2fyhj0GlUCvs/kjHoNCqF/R/LGHQalcL+T2QMOo1KYf+nMgadRmX/Z65n0GlU9n/uegadRqWw/wsZg06jUtj/pYxBp1Ep7P9KxqDTqBT2r5Ix6DQqhf1fyxh0GpXC/m9kDDqNSmH/ahmDTqNS2L9GxqDTqBT2fytj0GlUCvu/kzHoNCqF/WtlDDqNSrn7nc6/lzHoNCqF/T/IGHQalf0/up5Bp1HZ/5PrGXQalf3rXM+g06js/9n1DDqNSmH/LzIGnUalsP9XGYNOo1LY/5uMQadRKez/Xcag06gU9v8hY9BpVAr718sYdBqVwv4/ZQw6jUph/18yBp1GpbB/g4xBp1Ep7N8oY9BpVAr7/5Yx6DQq+2tcz6DTqK+5/3u7aT6dTqfT6XQ6nU6n0+l0Op1Op9PpdDqdTt+Snv693f9lnv1jvteaZ/+Y77Xm2T8K/H/czt08+8eD/5/Zs39c+Dxt81H37Sh71LF6OvvyR1z/iO951P/+Ubd/67Yfd59vfd/z/Xg9n4/JYfb/+Zx4Eo/Nrf17Es/BJ/G43TpPvrkfm8PsEdu8dSwf9zXkSez/k3jsbu3nY5+3j/lceJxj/bj78biP+QvpsXvcY/kk7t8L6fXocV9Xbh3PJ3H/Hvf143Hfs59E9iQ+Bzzfz5UX0n7ePK9vPP8e+332cT87Peb3vZCO5/N9nt16DXnc99rHfS1/3Nee5/v5/k353sd+j3/Mz91P4ng/7n14Ib0fP9/ZE3mMnsC/Mb81vM+d5+D/fW7d95vn52OeLy+kz6dP4nXwSTyPnsR77vP9XvW4nxefxL/7H/ezyJP4N+6TeI497s8nn+/n5hP5udQjvu9Rj8GTeH14vj+TP9/7+c39c81H/t7hEdc/6rF83GP0uJ8bHvfnTE/i+765z7lbv4O7/N5ufsR/fvN8ebJbP7zJ5+3Lt+6bO7+8QL68MPbi/PKt/cv8WN/1TbqZJ3aTD2342blbLfp1eNRK0YVtur9ydSISpgerTD+zvUetKF3YyCQigZWFeb6/kUetPF3YyCQigZWFebm/I49aobqwkUlEAisL83r/ztxayXojk4gEVhbmcP+A3FrteiOTiARWFuZ4/6DeWhF7I5OIBFYW5nT/gbm1avZGJhEJrCzM+f6De2tl7Y1MIhJYWZi3+yfIrdW3NzKJSGBlYS73T7JbK3RvZBKRwMrCXO+fqLdW8d7IJCKBlYW53T/Zb630vZFJRAIrC3O//4S5tRr4RiYRCawszOP+k+5RK4YXNjKJSGBlYb53/4n7qJXFCxuZRCSwsjDv95/8j1qBvLCRSUQCKwvz69x/AXnUSuWFjUwiElhZmJkOVjTfyCQigZWFmelg1fONTCISWFmYmQ5WRt/IJCKBlYWZ6WD19I1MIhJYWZiZDlZY38gkIoGVhZnpYBX2jUwiElhZmJkOVmrfyCQigZWFmelgNfeNTCISWFmYmQ5WfN/IJCKBlYWZ6WBV+I1MIhJYWZiZDlaO38gkIoGVhZnpYHX5jUwiElhZmJm4tQL9RiYRCawszEw3VqkvbGQSkcDKwnxXH3FjNfvCRiYRCawszEzcWvV+I5OIBFYWZiZurYy/kUlEAisLM9PB6vkbmUQksLIwMx2ssL+RSUQCKwsz08Eq/BuZRCSwsjAzHazUv5FJRAIrCzPTwWr+G5lEJLCyMDMdrPi/kUlEAisLM9NBK8BGJhEJrCzMTAfNARuZRCSwsjAzHbQLbGQSkcDKwsx00ECwkUlEAisLM9NBS8FGJhEJrCzMTAdNBhuZRCSwsjAzHbQdbGQSkcDKwsx0oxGhsJFJRAIrCzPTQXPCRiYRCawszEwH7QobmUQksLIwMx00MGxkEpHAysLMdNDSsJFJRAIrCzPTQZPDRiYRCawszEwHbQ8bmUQksLIwMx00QmxkEpHAysLMdNAasZFJRAIrCzPTQbPERiYRCawszEwH7RMbmUQksLIwMx00VGxkEpHAysLMdNBisZFJRAIrCzPTQdPFRiYRCawszEzcasPYyCQigZWFmemgMWMjk4gEVhZmphutGoWNTCISWFmYmbjVvrGRSUQCKwszE7caOjYyiUhgZWFmOmjx2MgkIoGVhZnpoOljI5OIBFYWZqaDNpCNTCISWFmYmQ4aQzYyiUhgZWFmOmgV2cgkIoGVhfnuNyMHzSMbmUQksLIwMx20k2xkEpHAysLMdNBgspFJRAIrCzPTQcvJRiYRCawszEwHTSgbmUQksLIwMx20pWxkEpHAysLMdNCospFJRAIrCzPTQevKRiYRCawszEwHzSwbmUQksLIwMx20t2xkEpHAysLMdNDwspFJRAIrCzPTQQvMRiYRCawszEwHTTEbmUQksLIwMx20yWxkEpHAysLMdNA4s5FJRAIrCzPTQSvNRiYRCawszEwHzTUbmUQksLIwMx2022xkEpHAysJ89xvbgwacjUwiElhZmJkOWnI2MolIYGVhZjpo0tnIJCKBlYWZ6aBtZyOTiARWFmYmbjXybGQSkcDKwsx00NqzkUlEAisLM9NBs89GJhEJrCzMTNxq/9nIJCKBlYWZiVsNQRuZRCSwsjAzHbQIbWQSkcDKwsx00DS0kUlEAisLM9NBG9FGJhEJrCzMTAeNRRuZRCSwsjAzHbQabWQSkcDKwsx00Hy0kUlEAisLM9NBO9JGJhEJrCzMTAcNShuZRCSwsjAzHbQsbWQSkcDKwsx00MS0kUlEAisLM9NBW9NGJhEJrCzMTAeNThuZRCSwsjAzHbQ+bWQSkcDKwsx00Ay1kUlEAisLM9NBe9RGJhEJrCzMTNxqmNrIJCKBlYWZ6aCFaiOTiARWFmamg6aqjUwiElhZmJkO2qw2MolIYGVhZjpovNrIJCKBlYWZ6aAVayOTiARWFmamg+asjUwiElhZmJkO2rU2MolIYGVhZuJWA9dGJhEJrCzMTActXRuZRCSwsjAzHTR5bWQSkcDKwsx00Pa1kUlEAisLMxO3GsE2MolIYGVhZjpoDdvIJCKBlYWZ6aBZbCOTiARWFmYmbrWPbWQSkcDKwszErYayjUwiElhZmJkOWsw2MolIYGVhZjpoOtvIJCKBlYWZ6aANbSOTiARWFmamg8a0jUwiElhZmJkOWtU2MolIYGVhZuKoeS2TiARWFmamg2a2jUwiElhZmJkO2ts2MolIYGVhZjpoeNvIJCKBlYWZ6aAFbiOTiARWFmamg6a4jUwiElhZmJkO2uQ2MolIYGVhZjponNvIJCKBlYWZ6aCVbiOTiARWFmamg+a6jUwiElhZmJm41W63kUlEAisLM9NBA95GJhEJrCzMTNxqydvIJCKBlYWZ6aBJbyOTiARWFmamg7a9jUwiElhZmJkOGvk2MolIYGVhZjpo7dvIJCKBlYWZ6aDZbyOTiARWFmYmbrX/bWQSkcDKwsx00BC4kUlEAisLM9NBi+BGJhEJrCzMd/+P+oOmwY1MIhJYWZiZuNVGuJFJRAIrCzPTQWPhRiYRCawszEwHrYYbmUQksLIwM3Gr+XAjk4gEVhZmJm61I25kEpHAysLMdNCguJFJRAIrCzPTQcviRiYRCawszEwHTYwbmUQksLIwMx20NW5kEpHAysLMdNDouJFJRAIrCzMTt1ofNzKJSGBlYWY6aIbcyCQigZWFmemgPXIjk4gEVhZmpoOGyY1MIhJYWZiZDlooNzKJSGBlYWY6aKrcyCQigZWFmemgzXIjk4gEVhZmpoPGy41MIhJYWZiZDloxNzKJSGBlYWY6aM7cyCQigZWFmYlb7ZobmUQksLIwMx00cG5kEpHAysLMxK2Wzo1MIhJYWZiZDpo8NzKJSGBlYWY6aPvcyCQigZWFmemgEXQjk4gEVhZmpoPW0I1MIhJYWZiZDppFNzKJSGBlYWbiVvvoRiYRCawszEwHDaUbmUQksLIwMx20mG5kEpHAysLMxK2m041MIhJYWZiZuNWGupFJRAIrCzPTQWPqRiYRCawszEwHraobmUQksLIwM3GreXUjk4gEVhZmJm61s25kEpHAysLMdNDgupFJRAIrCzPTQcvrRiYRCawszEwHTbAbmUQksLIwMx20xW5kEpHAysLMdNAou5FJRAIrCzMTt1pnNzKJSGBlYWY6aKbdyCQigZWFmemgvXYjk4gEVhZmpoOG241MIhJYWZiZDlpwNzKJSGBlYWY6aMrdyCQigZWFmYlbbbobmUQksLIwMx007m5kEpHAysLMdNDKu5FJRAIrCzPTQXPvRr77S1sigZWF+e6vgLnV7ruRSUQCKwsz00ED8EYmEQmsLMx3f8XNrZbgjUwiElhZmJkOmoQ3MolIYGVhZjpoG97IJCKBlYWZ6aCReCOTiARWFmamg9bijUwiElhZmJkOmo03MolIYGVhZuJW+/FGJhEJrCzMTAcNyRuZRCSwsjAzHbQob2QSkcDKwszErabljUwiElhZmJm41ca8kUlEAisLM9NBY/NGJhEJrCzMTDdanQsbmUQksLIwM/Go9ufCRiYRCawszEzcaoneyCQigZWFmYlbTdIbmUQksLIwM3GrbXojk4gEVhZmJm41Um9kEpHAysLMxK3W6o1MIhJYWZiZuNVsvZFJRAIrCzMTt9qvNzKJSGBlYWbiVkP2RiYRCawszEzcatHeyCQigZWFmYlbTdsbmUQksLIwM3GrjXsjk4gEVhZmJm41dm9k7laaiARWFmYmbrV6b2QSkcDKwszEo5q/CxuZRCSwsjAz8aiG8MJGJhEJrCzMTDyqSbywkUlEAisLMxOPahwvbGQSkcDKwsx00Ey+kUlEAisLMxO32ss3MolIYGVhZjpoON/IJCKBlYWZ6aAFfSOTiARWFmamg6b0jUwiElhZmJkO2tQ3MolIYGVhZjpoXN/IJCKBlYWZiVut7BuZRCSwsjAzHTS3b2QSkcDKwsx00O6+kUlEAisLMxO3GuA3MolIYGVhvlvNhke1xBc2MolIYGVhZrrRJl/YyHfN8kQCKwsz02vurwEwzafT6XQ6nU6n0+l0Op1Op9PpdDqdTqfT6XQ6nU6n0+l0Op1Op9PpdDqdTqfT6XQ6nU6n0+l0Op2+pT29BsA555xzzjnnnHPOOeecc84555xzzjnnnHPOOeecc84555xzzjnnnHPOOeecc84555xzzjnnnHPOOeecc84555xzzjnnnHPOOeecc84555zzLT7nGgDnnHPOOeecc84555xzzjnnnPNtcMJ0f33Ap+fuwsJ6CSKJzEah0ugM7rFP93+y8O349nwHXpfvyOvxnXh9vjNvwBvyRnwX3pjvynfju/M9eBO+J9+L782b8mZ8H96c78v34/vzFvwAfiA/iB/MD+Et+aH8MH44b8Vb8yN4G34kP4ofzdvyY/ix/Dh+PG/H2/MO/AR+Ij+Jn8xP4afyjvw0fjrvxDvzLrwrP4N342fys/jZ/Bx+Lu/Oz+M9+Pn8At6T9+IX8t68D+/L+/H+fAAfyAfxwXwIH8qH8eG8iI/gI3kxT/ESXsrLeDkfxSt4Ja/iF/FqPpqP4RfzS/il/DI+ll/Or+BX8nF8PL+KX82v4dfy6/j1/AY+gd/IJ/Kb+M38Fn4rv41P4rfzyXwKn8rv4Hfyu/jd/B5+L5/Gp/P7+P38Af4gn8Ef4g/zR/ijfCZ/jM/ij/Mn+JP8KT6bz+FP82f4XD6Pz+fP8gV8IX+OL+LP8xf4Yv4if4m/zF/hr/LX+Ot8CX+Dv8nf4kv5Mr6cv83f4e/yFfw9vpK/zz/gH/KP+Mf8E/4p/4x/zr/gX/Kv+Cr+Nf+Gr+Zr+Lf8O76Wf89/4D/yn/g6/jP/hf/Kf+O/8z/4ev4n/4tv4Bv537zm8hrwAlik9HQ6fcu4+3LOOed8253zNeC15u5z+t2/he7+7eHozG/73u0tR17avVe97t3H53gdvc8bfYKoXqJ8Hb3v23yAaLtE23Oj/FobnO+i9/uyz7ls8FVXG3w6+oA3+JLLBl91tcGrKF+iZza43EUf+IpvuGzw7jN/uo4+6KVvIiqXKD8Uff3HXm7ro69u6yrKr7XB9S760Le6d9ngq682eD/6ije4bPDVVxu8ivIlemaD4S768K/+kssGP+pqg09HL/qsT7ps8KOuNngV5Uu0X0f3H8rpEk2XKD54KHfRy6f7z4pno/sPyr1LtFxH9w/vuET5Onrxx33iZQ9ffrWHV1G+RP06+pDxVaL1Eo3n3tZyie49dw/nS7Q/935Nr3W/0l30/p+xXO7XK6/uV3pwboxLtF5H9x/lfonCdXT/8WqXKF5HH/nKL7qch6+8OlBPRy9914+/HKhXXh2oqyhfonIdPfXF7yBKl6g+97biJWrP3cNwifpz79f6WvcrPzij7u7XK67uV37wUNZLlK6j+ztfLlG+jl7+FV93ucuvuLrLV1G+ROW5G0yXqD53N+Ilas/d+fBaO789vPMvudr57eGdf8nVzm8P7/xLLjv/Zg9H+RKV524wXaJnDm95+LR56moPy8OnzVNXe1genADlEuXr6MEZ9dTV4S0Pn1FPXe1hefiMeurq8JaHz6inrg5vefiMeurqSVQffnF42dWTqD54mvdLFK6jD3v1G1+Oxsuu7nJ9sBvlEuXr6MGrzcuu7vJVlC9Re+5thUvUn7uH6yUaz71fyyW6dx09eCG6i/br6MEL0V00XaL24GX57rX37odX83X0vp/6npfXqJdeHd724F1vXKJ0HT1443jp1dFoD787vPTqBLj6rnSJntnDfhe912d+9mUPP+JqD/uD+3XvEsXr6P4xrJcoXUcP3is/4mo3rqJ0iZ7ZjXEXvefrv8VlN150dVvj4Tf6F11tcDx4MU+XqD73u+IleuY8vPfgyN+dhy++uq17D39KefHVBq+ieInaczcYLtEzt7U//BHrI6++6yoKl6hfR/cflPUS3T3K62uenqcPWnjNa579b/Hqcrq6nK8ub1eXy9Xl+szl/wMUj0iV",
        "plains", "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",
        "mixed_forest", "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",
        "sand_red_sand", "eJzt23ncLXMdwPG5LtdDfc8XyS5biGyhiEJZs0Qlqa4liuveNilJFyGy70mpLK1UpE2hIrK1KktpV6iUCinE7T3nnMnvjjkP/lEvPc/r9X6dz52ZZ2bObGfOec6dsvKRf6yqah5mVoOfL1TVHC8e9nuqauzaYc+hvzvsSfp7D/fi3xr2s6pqlS8N+x9VteZbh5O8sKrW+uZw+NuYc9iTB/OtJk8adNIj6jasR5Am6hGkCXsEaUY9gpxLE+QUTZBza4Ic0wTpSfcIcl5NkE/RBPlUTZBWqkeQVrJHkFa6R5DzaYKcXxPkApogn6YJckFNkE/XBLmQJsiFNUEuoglyUU2Qi2mCXFwT5BKaIJfUBPkMTZBLaYJcWhPkMpogl9UEuZwmyGdqglxeE+QKmiBX1ATpAOkR5EqaIFfWBPlsTZCraIJcVRPkapogV9cEuYYmyOdoglxTE+RamiDX1gT5XE2Qz9MEuY4myHU1QT5fE+R6miDX1wT5Ak2QToIeQW6gCXJDTZAbaYJ8kSZIJ2SPIDfWBLmJJshNNUFupglyc02QW2iCfIkmyC01QW6lCXJrTZDbaIJ8qSbIbTVBbqcJ8mWaIF+uCfIVmiC31wT5Sk2QO2iCfJUmyB01Qb5aE+RrNEG+VhPkVE2QO2mC3FkT5C6aIHfVBPk6TZC7aYLcXRPk6zVBvkET5B6aIPfUBDlNE+RemiCna4KcoQnyjZog36QJ8s2aIN+iCdIFt0eQe2uCdNHtEeQ+miDfrgnyHZog99UE+U5NkPtpgnyXJsj9NUG+WxOkF5UeQR6gCfJATZAHaYL0gtMjyIM1QR6iCfJQTZDv1QR5mCbIwzVBvk8T5BGaII/UBHmUJsijNUEeownyWE2Qx2mCPF4T5AmaIE/UBHmSJsiTNUGeogny/ZogT9UE+QFNkKdpgvygJsgPaYI8XRPkhzVBfkQT5Ec1QZ6hCfJMTZBnaYI8WxPkxzRBflwT5Cc0QX5SE+SnNEF+WhPkOZogz9UE+RlNkJ/VBPk5TZDnaYI8XxPk5zVBXqAJ0k1NjyC/qAnSzUqPIL+sCfIrmiAv1AT5VU2QX9MEeZEmyIs1QV6iCfLrmiC/oQnSjVGPIC/VBHmZJkg3WD2CvFwT5BWaIL+tCfJKTZBXaYK8WhPkNZog3eT1CPI7miDd8PUI0g1fjyC/rwnyB5ogf6gJ8jpNkD/SBPljTZDXa4K8QRPkjZogb9IE+RNNkD/VBHmzJsifaYL8uSbIX2iC/KUmyF9pgvy1JsjfaIK8RRPkbzVB/k4T5K2aIG/TBHm7Jsjfa4L8gyZIN+E9grxDE+SfNEH+WRPknZog/6IJ8q+aIP+mCfIuTZB3a4K8RxPk3zVB3qsJ0o18jyD/qQnyPk2Q92uCfEAT5L80QT6oCfIhTZCzNDFr8B6g2rfw7sLE8InhE8Mnhk8Mnxg+MXxi+MTwieETw590w/t/E3jx8A8JMwd/RKj1/4AwczCu/oNDM7xWT9v/o8XMh6et1cOa4fW/m3HN/OvHrvnX48phjXp4M59y2np9mmmb9WnGl8+hHt4o17/+/Vq5Pl3Tt9elPZ9m3Hjz6Vr/clvX45th7e3TPJ/2fmn2STN9s4xy/uVym/UftV/K59WsT/M7Xc+rmU+5jPZyu7ZDuc/L/d4oj5PyOY+aftR+GW/9m/mU26HZluNN3z5+2sdtuT7lfiv3b9fzLc+9cj92HZ9d+3fUdmivSzOsvX265tPev+V5XU7brGM5vBzXtT7tY7/czl3nb9f8m/N3vOOza7mjjrdR82muM432eVpu167zrtzO5TZtb59yO7evJ83vd00/6vrTbMtyPZv1L6/P5bWhnL59nrbnP+p60t7+zeOo14Vmuc3rS3k8NPMpj5Xy+t91nrbn037daa9P1/DyuZXnS7n9u/Z717YZ7/wtt3PXdabc76O2Z/t1atRy29fD8ngup2/Pp319GPU63vW8uq7/5XNrH2+jrpPt87frelXPp30/UJ6/5fFWXlfb0496HXks9wnl/c9jPR66ltt1HrWnb2//5th8tOthe7+X58uo62Q5fXn+Ptr6j/d6Wt5XdR0n411/2sfnqOOt63VkvOnL9W+2TXn8lMd/+zrTvg50va61j/3Huj1r4+3fUa/jXfc/5etLuezJ1eSJn4mf/+OfavafOSdV1aTmW3N68K25avBNurmYwtyMVYNv883LU3gq9bfp6m/V1d+um4/5WYCnsSBPZyEWZhEWZTEWtyiqJViSZ7AUS7MMy7Icz2R5VmDFqv8twGolVubZrMKqrMbqrMFzWJO1WJvn8jzWYV2ez3qszwt4IRuwIRvxIuqLxsZswqZsxuZswUvYkq3Ymm14KduyHS/j5byC7XklO/AqduTVvIbXMpWd2Jld2JXXsRu783rewB7syTT2YjozeCNv4s28hfqCuHc1+CbkPrydd1SDD0LeyX68i/2rwYci9cXzAA7koKr/rczqYA7hUN7LYRzO+ziCIzmKozmGYzmO4+1TqhM4kZM4mVN4P6fyAU7jg3yI0/kwH+GjnMGZnMXZfIyP8wk+yaf4NOdwLp/hs3yO8zjfOlB9nguq/jdOqy9Sv3h8ma9wIV/la1zExVzC1/kG9YvipVxGfUN6OVfwba7kKq7mGupvsH6H+tur9bdWv88P+CHX8SN+zPXcwI3cxE/4qXWlupmf8XN+wS/5Fb/mN9zCb/kdt3Ibt/N7/kD9Lds7+BN/5k7+wl/5G3dxN/fwd+6t+t+grf7JfdzPA/yLB3mIWcNrQvMB2hP1WP0Xljnx+OTfp/8L6/AkWNf+NWHmwzfqzY15+di+Ie/6QK59Q1/e2Jcf4NSP9f8MaM+7Pa/mTXF7Xbrm3R7fvJktx4/3IUHz3NvLG7X89nqW82mW257fqG3b3qZd8yvffLff/Hb9fvP8m+WO2r7tN//tD1XK9Xi0x/7//Jj5yDfcpXLftNd11D7p2jfNv9vzqf+XSXtc13HR/nCk648G5fYqj6P2fiq383jHQ/lhZ39dr33ksVkec+1juxzetY5d/26vS7mOo46d+nHSnOO92ZjyRL2reVwLfRxr9RgnHV63+++j5quKn3rIHINc+uGh9ful+v3FRlX/vdWkDXe+49SxJebfcrF9lqxv9zeZbdTc6xq1zXDUpuWonZa92aith6M2K0dN3WCmUdsORzXLGpt9WdOLZY3NvqzpxbLGZl/W9GJZY7Mva3qxrHn6M7z90uGyZhTLGoya/LbhsmYUy+qP2mmp5YbLmlEsqz9q6nrXD5dVznDe/m89cNtwhtOKGQ5GLXzhcIbTihn2R01d6/DhDKcNnk81eVb/Z7D7Zs36z7/mKnpK0XMXPVb0PEXP2/S/Af0UQ+A=",
        "rock", "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",
        "snow", "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",
        "water_edge", "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",
        "village", "eJzV1gmMTVcYwPHzllmsMy2xi7ZjndIEsS+hqrU1ioxgQmytfYllIkzs2wRJiSVVa7QlpWgpgiCkBhmt8VJLiCVhxC5BYh//b945733vGaKJ0cxNfu9+dznnfPecc989sckZN4wxxU1486CsjX1YFY7jj6k4S8XHbew1pmJPGycZ0+yLcJ0pX9m4ijGpK2xcx5i0bBs3IA7YuB78KgfJyS8/OfbAay/ITXGIRxEURTH7QCVQEglIxAf4EKVQGmXsg5ZDeaqFqYCKqITK+Agf45PgA5mqqIbqqIGaqIVkfIrawYcyn6Eu6gcfzDREIzRGEzRFMzRHC7REK7TG52gD6by2+BLSee3QHh3QEZ3wNXl3Zv8NuqAruiEF3dEDMiC9kIre6IO+6If+GICB+BbfYRAGYwiGYhiGYwRGYhRtwozGGIzFOIxHGiZgIiYhHZMxBVMxDdMxAzMxC7MxB3ORgXmYjwX4HguxCIuxBEuxDD9gOX6ETKiVJjhZV2MN1mIdfsLP+AXrsQG/YiM24TdsxhZsxe/4A9uwHX9iB3ZiF3ZjD/ZiH/bjAA7iEP7CYWTiCI5CXhx5YeRF+Rv/4ARk8p+ETPx/cQqncQZncY6+hjmPC7iIS7iMK7hqgu/ENVzHTdzCbdzBXdzDfTzAQzzCYzzBUzzDc7ygLea3Fz744WGuexhXL3zww8MYe3g+L3zww5Np8yhZQEwB1l2Q/o+8C2tfFUbvsa+98lGU/8eE4HG99Lc/J++mfHSFfIjloyux7OWjK3t3Xl+Xj7lcl70r78q5vSwK5J68cgF73h678vp+V1d0Gy63ULns8P0uh4i6bBu6nM7fldXldJ6SdyiXQLicPufa1TnrOvSxLudycsfumiyUXP7R13V9TnR/y33uWXX5/Poqepxdn+o6HT03QmOSHa5HH+scI46zVU7qnHv2V9oPRO51H+h69LhEj41rR9cfGt+syLwj8oqap3r+RIxHILItPbe0vMWwrD3K2rbdglify7ILYn1O1gU5vny32PxPv2Z7493/rap31W4Mmz/8Y3skuG+V9yur+MREE9qS4qcavcmq3pebtwXL5uaGjmJUHKviOBXHq7iIiou6+CXLDKwT");

    static { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void unchangedMutationMapsMatchTheFrozenOracleAndForestUsesRepairedDensity() {
        ArrayList<String> missing = new ArrayList<>();
        for (Fixture fixture : fixtures()) {
            PreparedChunkPlan plan = compile(fixture);
            byte[] actual = fixtureImage(plan);
            String compressedReference = REFERENCE_62A89.get(fixture.name);
            if (compressedReference == null || compressedReference.isBlank()) {
                missing.add(fixture.name + "=" + compress(actual));
                continue;
            }
            byte[] expected = decompress(compressedReference);
            if (fixture.name.equals("mixed_forest")) {
                assertFalse(Arrays.equals(expected, actual),
                    "forest fixture must record the accepted lower crown retention");
                assertRepairedForestDensity(plan);
                continue;
            }
            assertArrayEquals(expected, actual,
                fixture.name + " coordinate mutation map");
            assertEquals(readHistogram(expected),
                WarheadPlanCompiler.statistics(plan).replacementHistogram(),
                fixture.name + " replacement histogram");
        }
        assertFalse(!missing.isEmpty(), () -> String.join("\n", missing));
    }

    private static void assertRepairedForestDensity(final PreparedChunkPlan plan) {
        int strippedLeaves = 0;
        int retainedLeaves = 0;
        for (PreparedSectionPlan section : plan.blockSections()) {
            int[] expected = section.expectedStateIdsUnsafe();
            int[] replacements = section.finalStateIdsUnsafe();
            byte[] categories = section.mutationCategoriesUnsafe();
            for (int index = 0; index < replacements.length; index++) {
                if (WarheadMutationCategory.fromWireId(categories[index])
                    != WarheadMutationCategory.VEGETATION
                    || Block.stateById(expected[index]).getBlock()
                        != Blocks.OAK_LEAVES) continue;
                if (Block.stateById(replacements[index]).isAir()) strippedLeaves++;
                else if (Block.stateById(replacements[index]).getBlock()
                    == Blocks.PALE_OAK_LEAVES) {
                    retainedLeaves++;
                }
            }
        }
        assertTrue(strippedLeaves > retainedLeaves,
            "blast-facing crowns should expose more trunk than they retain");
        assertTrue(retainedLeaves > 0,
            "the repaired gradient must remain a gradient, not remove every crown");
    }

    private static PreparedChunkPlan compile(final Fixture fixture) {
        PreparedImpactSpec impact = new PreparedImpactSpec(UUID.nameUUIDFromBytes(
            ("62a89-map-" + fixture.name).getBytes(StandardCharsets.UTF_8)), CENTER,
            WarheadPayloadType.NUCLEAR, WarheadYield.TACTICAL_NUCLEAR,
            fixture.seed, true);
        WarheadFootprint footprint = WarheadFootprintCalculator.calculate(
            impact.payload(), impact.yield(), impact.target());
        return WarheadPlanCompiler.compile(impact, footprint,
            fixture.snapshot(impact), WarheadStatePalette.capture());
    }

    private static List<Fixture> fixtures() {
        return List.of(
            new Fixture("crater_rock", CRATER_CHUNK, 0x620001L,
                Blocks.STONE, WarheadSnapshotFlags.COMMON_ROCK, true, false, List.of()),
            new Fixture("plains", AFTERMATH_CHUNK, 0x620002L,
                Blocks.GRASS_BLOCK, WarheadSnapshotFlags.SOIL
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, false, List.of()),
            new Fixture("mixed_forest", AFTERMATH_CHUNK, 0x620003L,
                Blocks.GRASS_BLOCK, WarheadSnapshotFlags.SOIL
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, false,
                forestTargets(AFTERMATH_CHUNK)),
            new Fixture("sand_red_sand", AFTERMATH_CHUNK, 0x620004L,
                Blocks.SAND, WarheadSnapshotFlags.SAND
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, true, List.of()),
            new Fixture("rock", AFTERMATH_CHUNK, 0x620005L,
                Blocks.STONE, WarheadSnapshotFlags.COMMON_ROCK
                    | WarheadSnapshotFlags.NATURAL_SURFACE
                    | WarheadSnapshotFlags.EXPOSED, false, false, List.of()),
            new Fixture("snow", AFTERMATH_CHUNK, 0x620006L,
                Blocks.SNOW_BLOCK, WarheadSnapshotFlags.SNOW
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, false,
                snowTargets(AFTERMATH_CHUNK)),
            new Fixture("water_edge", AFTERMATH_CHUNK, 0x620007L,
                Blocks.GRASS_BLOCK, WarheadSnapshotFlags.SOIL
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, false, List.of(), true),
            new Fixture("village", AFTERMATH_CHUNK, 0x620008L,
                Blocks.COBBLESTONE, WarheadSnapshotFlags.COMMON_ROCK
                    | WarheadSnapshotFlags.NATURAL_SURFACE, false, false,
                villageTargets(AFTERMATH_CHUNK)));
    }

    private static List<Target> forestTargets(final ChunkPos chunk) {
        ArrayList<Target> result = new ArrayList<>();
        for (int z = 2; z < 16; z += 4) {
            for (int x = 2; x < 16; x += 4) {
                result.add(new Target(chunk.getMinBlockX() + x, 65,
                    chunk.getMinBlockZ() + z, Blocks.OAK_LOG,
                    WarheadSnapshotFlags.LOG | WarheadSnapshotFlags.NATURAL_TREE));
                result.add(new Target(chunk.getMinBlockX() + x, 66,
                    chunk.getMinBlockZ() + z, Blocks.OAK_LOG,
                    WarheadSnapshotFlags.LOG | WarheadSnapshotFlags.NATURAL_TREE));
                result.add(new Target(chunk.getMinBlockX() + x, 67,
                    chunk.getMinBlockZ() + z, Blocks.OAK_LEAVES,
                    WarheadSnapshotFlags.LEAVES));
                result.add(new Target(chunk.getMinBlockX() + x, 68,
                    chunk.getMinBlockZ() + z, Blocks.OAK_LEAVES,
                    WarheadSnapshotFlags.LEAVES));
            }
        }
        return List.copyOf(result);
    }

    private static List<Target> snowTargets(final ChunkPos chunk) {
        ArrayList<Target> result = new ArrayList<>();
        for (int z = 1; z < 16; z += 3) {
            for (int x = 1; x < 16; x += 3) {
                result.add(new Target(chunk.getMinBlockX() + x, 65,
                    chunk.getMinBlockZ() + z, Blocks.SNOW,
                    WarheadSnapshotFlags.SNOW | WarheadSnapshotFlags.FRAGILE));
            }
        }
        return List.copyOf(result);
    }

    private static List<Target> villageTargets(final ChunkPos chunk) {
        ArrayList<Target> result = new ArrayList<>();
        for (int z = 2; z <= 12; z += 5) {
            int x = chunk.getMinBlockX() + 4 + z / 5;
            int worldZ = chunk.getMinBlockZ() + z;
            result.add(new Target(x, 65, worldZ, Blocks.GLASS,
                WarheadSnapshotFlags.GLASS | WarheadSnapshotFlags.FRAGILE));
            result.add(new Target(x, 66, worldZ, Blocks.OAK_LOG,
                WarheadSnapshotFlags.LOG));
            result.add(new Target(x, 67, worldZ, Blocks.OAK_PLANKS,
                WarheadSnapshotFlags.PLANK));
            result.add(new Target(x, 68, worldZ, Blocks.COBBLESTONE,
                WarheadSnapshotFlags.COBBLE));
            result.add(new Target(x + 1, 65, worldZ, Blocks.TALL_GRASS,
                WarheadSnapshotFlags.FRAGILE | WarheadSnapshotFlags.SURVIVAL_SENSITIVE));
        }
        return List.copyOf(result);
    }

    private static byte[] fixtureImage(final PreparedChunkPlan plan) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(0x62A89EA);
                TreeMap<Integer, Long> histogram = new TreeMap<>(
                    WarheadPlanCompiler.statistics(plan).replacementHistogram());
                output.writeInt(histogram.size());
                for (Map.Entry<Integer, Long> entry : histogram.entrySet()) {
                    output.writeInt(entry.getKey());
                    output.writeLong(entry.getValue());
                }
                output.writeInt(plan.chunk().x());
                output.writeInt(plan.chunk().z());
                output.writeInt(plan.activationTick());
                output.writeInt(plan.blockSections().size());
                for (PreparedSectionPlan section : plan.blockSections()) {
                    output.writeInt(section.sectionY());
                    output.writeByte(section.phase().ordinal());
                    write(output, section.localIndicesUnsafe());
                    write(output, section.expectedStateIdsUnsafe());
                    write(output, section.finalStateIdsUnsafe());
                    write(output, section.mutationCategoriesUnsafe());
                    write(output, section.semanticMaskUnsafe());
                    write(output, section.survivalMaskUnsafe());
                    write(output, section.supportCheckMaskUnsafe());
                }
                output.writeInt(plan.fireMutations().size());
                for (PreparedFireMutation fire : plan.fireMutations()) {
                    output.writeInt(fire.x()); output.writeInt(fire.y());
                    output.writeInt(fire.z()); output.writeBoolean(fire.crater());
                    output.writeBoolean(fire.tree()); output.writeBoolean(fire.customFire());
                    output.writeFloat(fire.intensity()); output.writeLong(fire.seed());
                }
                output.writeInt(plan.biomeSections().size());
                for (PreparedBiomeSectionPlan biome : plan.biomeSections()) {
                    output.writeInt(biome.sectionY()); output.writeLong(biome.quartMask());
                }
            }
            return bytes.toByteArray();
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static void write(final DataOutputStream output, final int[] values)
        throws java.io.IOException {
        output.writeInt(values.length);
        for (int value : values) output.writeInt(value);
    }

    private static void write(final DataOutputStream output, final byte[] values)
        throws java.io.IOException {
        output.writeInt(values.length);
        output.write(values);
    }

    private static void write(final DataOutputStream output, final BitSet values)
        throws java.io.IOException {
        long[] words = values.toLongArray();
        output.writeInt(words.length);
        for (long word : words) output.writeLong(word);
    }

    private static Map<Integer, Long> readHistogram(final byte[] image) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(image))) {
            assertEquals(0x62A89EA, input.readInt());
            int count = input.readInt();
            TreeMap<Integer, Long> histogram = new TreeMap<>();
            for (int index = 0; index < count; index++) {
                histogram.put(input.readInt(), input.readLong());
            }
            return histogram;
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static String compress(final byte[] value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(bytes)) {
                deflater.write(value);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static byte[] decompress(final String value) {
        try (var inflater = new java.util.zip.InflaterInputStream(
            new java.io.ByteArrayInputStream(Base64.getDecoder().decode(value)))) {
            return inflater.readAllBytes();
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private record Target(int x, int y, int z,
        net.minecraft.world.level.block.Block block, int flags) { }

    private record Fixture(String name, ChunkPos chunk, long seed,
        net.minecraft.world.level.block.Block surfaceBlock, int surfaceFlags,
        boolean crater, boolean alternatingRedSand, List<Target> targets,
        boolean waterNear) {
        private Fixture(final String name, final ChunkPos chunk, final long seed,
            final net.minecraft.world.level.block.Block surfaceBlock,
            final int surfaceFlags, final boolean crater,
            final boolean alternatingRedSand, final List<Target> targets) {
            this(name, chunk, seed, surfaceBlock, surfaceFlags, crater,
                alternatingRedSand, targets, false);
        }

        private WarheadChunkSnapshot snapshot(final PreparedImpactSpec impact) {
            int air = Block.getId(Blocks.AIR.defaultBlockState());
            int source = Block.getId(surfaceBlock.defaultBlockState());
            int[] motion = new int[256];
            int[] terrain = new int[256];
            int[] columns = new int[256];
            Arrays.fill(motion, 64);
            Arrays.fill(terrain, 64);
            if (waterNear) Arrays.fill(columns, WarheadSnapshotFlags.WATER_NEAR);
            int[] surfaceStates = new int[256 * WarheadChunkSnapshot.SURFACE_LAYERS];
            int[] surfaceStateFlags = new int[surfaceStates.length];
            Arrays.fill(surfaceStates, source);
            Arrays.fill(surfaceStateFlags, surfaceFlags);
            for (int column = 0; column < 256; column++) {
                surfaceStates[column] = air;
                surfaceStateFlags[column] = WarheadSnapshotFlags.AIR;
                if (alternatingRedSand && (column & 1) != 0) {
                    for (int layer = 1; layer < WarheadChunkSnapshot.SURFACE_LAYERS; layer++) {
                        surfaceStates[layer * 256 + column] =
                            Block.getId(Blocks.RED_SAND.defaultBlockState());
                        surfaceStateFlags[layer * 256 + column] =
                            WarheadSnapshotFlags.RED_SAND
                                | WarheadSnapshotFlags.NATURAL_SURFACE;
                    }
                }
            }
            int minimumCraterY = 0;
            int maximumCraterY = -1;
            if (crater) {
                NuclearTerrainProfile profile = NuclearTerrainProfile.forYield(impact.yield());
                minimumCraterY = (int)Math.floor(impact.target().y)
                    - (int)Math.ceil(profile.downwardRadius()) - 1;
                maximumCraterY = (int)Math.floor(impact.target().y)
                    + (int)Math.ceil(profile.upwardRadius()) + 1;
            }
            int craterCells = Math.max(0, maximumCraterY - minimumCraterY + 1) * 256;
            int[] craterStates = new int[craterCells];
            int[] craterFlags = new int[craterCells];
            float[] resistance = new float[craterCells];
            Arrays.fill(craterStates, Block.getId(Blocks.STONE.defaultBlockState()));
            Arrays.fill(craterFlags, WarheadSnapshotFlags.COMMON_ROCK);
            Arrays.fill(resistance, 6.0F);
            long[] positions = new long[targets.size()];
            int[] states = new int[targets.size()];
            int[] flags = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                Target target = targets.get(index);
                positions[index] = BlockPos.asLong(target.x, target.y, target.z);
                states[index] = Block.getId(target.block.defaultBlockState());
                flags[index] = target.flags;
            }
            long[] revisions = new long[24];
            Arrays.fill(revisions, 62_089L);
            return new WarheadChunkSnapshot(chunk, 62_089L, -4, revisions,
                -64, 319, minimumCraterY, maximumCraterY, motion, terrain, columns,
                surfaceStates, surfaceStateFlags, craterStates, craterFlags, resistance,
                positions, states, flags);
        }
    }
}
