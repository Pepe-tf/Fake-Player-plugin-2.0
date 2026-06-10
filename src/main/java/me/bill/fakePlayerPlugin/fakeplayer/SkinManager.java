package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

public final class SkinManager {

    private static final List<String> DEFAULT_FALLBACK_ACCOUNT_POOL = List.of(
            "1Dont3now_tv",
            "1ns0mn1a_bot",
            "20JP",
            "21PilotLyfe",
            "2hnoah",
            "303_Gaming",
            "5unah",
            "6abz",
            "6kid",
            "9222",
            "_Festivities_",
            "_mooshimon",
            "_VES",
            "A_Quip",
            "Aaronblatt",
            "abblebeezz",
            "AbiCoops",
            "Acid_Pie",
            "AdamnAdam",
            "AdamNotAvailable",
            "AdamPlays",
            "Agourk",
            "Agroed",
            "Ah0j",
            "AhinaHayamaTTV",
            "AHRCEUS",
            "airyawn",
            "Aiyri",
            "Ajaxmvb",
            "akc0303",
            "Akirini",
            "ALBrassard",
            "Aliface",
            "AlinaOverpowered",
            "allthatchelsea",
            "AlphaFounded",
            "Alphyre_YT",
            "Alsogone",
            "AltemusX",
            "Altermond",
            "aluee",
            "Alzakz",
            "Am_ehx",
            "Amisu",
            "Amzo_",
            "An_Accountant",
            "Anchyuwu",
            "AnEmily",
            "Anfasith",
            "AnibaeVT",
            "Anima_Ryuu",
            "AnkaaGrey",
            "ann3xiety",
            "Annekie",
            "Anniel96",
            "annupoica",
            "Antonisu",
            "AntToesKnees",
            "Aopks",
            "Apollo30",
            "AppleW",
            "aprilmaple5620",
            "Aprite",
            "ARareLemon",
            "AriesAkana",
            "Arin524",
            "arrf",
            "Aryxion",
            "Asaf_Playz",
            "AshBroz1234",
            "ashsart23",
            "Ashuen",
            "Aslinxzz",
            "AsoEu",
            "AstraCA",
            "AStrangeCreature",
            "Asttaroth",
            "asvuep",
            "Atomsee",
            "AuddRee",
            "Aulig",
            "Aur1e",
            "AuroraKatVA",
            "avieluu",
            "Avoma",
            "aw3someg1rl",
            "awesomeness0880",
            "AwsomeHoneyDog",
            "Ax0lott",
            "ayqh",
            "b00fswag",
            "babyorbii",
            "BadRexx",
            "Balc0n",
            "BatzyBear",
            "bearbubb",
            "Bearcraft02",
            "bebopskye",
            "beefyboulevard",
            "beepbeep_cheeryo",
            "BenDotEXE",
            "Berna7224",
            "Bimpy416",
            "Bingostill",
            "BinnBean",
            "bipolarobot",
            "Bittersw3et",
            "Bizzy_Brit",
            "Blinci",
            "BlitzStream",
            "bluetenmeer",
            "bolh3h",
            "BombsterG",
            "BonkersJack",
            "BooPenelopeApple",
            "BottledSalad",
            "BowlV2",
            "BOX_Eggsotic",
            "boysbehating",
            "braedy12",
            "BrasenPlayz",
            "BreadleyYourHero",
            "Broodling",
            "BROWN_YT",
            "Bumpkin_Boii",
            "Bun_Bunana",
            "BunnMom",
            "Busvicke123",
            "ButtonMash5000",
            "Byzantonia",
            "caiuwus",
            "callkhada",
            "CallMeCass",
            "CaluMerge",
            "CamYeaaH",
            "candiivulpix",
            "Canopaa",
            "CaptainConer",
            "captainlights",
            "CaramelExpert59",
            "cardinroo",
            "CareBearKarliTV",
            "careye",
            "CarlyPumpkin",
            "CarnivalCow",
            "CarrieSprout",
            "CarterPJ",
            "Catrinformation",
            "CeliaRAT",
            "CerealTomato",
            "Cernunnos280",
            "ChairX_",
            "charlesispi",
            "Charliebubblegum",
            "charm_y3",
            "chcw",
            "CheByrechik",
            "CheddarE",
            "CheesyMcRibby",
            "Chiara_00",
            "Chiffoi",
            "ChimeraSea",
            "Chiolite",
            "chloq",
            "chrislaban",
            "CisiCisiC",
            "Civilmeowmeow",
            "CleverlyStupid",
            "CocoTakumi",
            "ColbytheWolby",
            "ColeRoll",
            "CometPal",
            "conee",
            "COOLMOIVE24",
            "corblii",
            "Corporal_Jerry",
            "CorsairPotato",
            "Coulrs",
            "Countess0327",
            "cperc",
            "CrabbySleet3973",
            "Craftulu",
            "Crester_Craft",
            "Crusty435",
            "CrysCross",
            "CrystalFang",
            "Cumi",
            "Cupcake6583",
            "cupOfGlass",
            "CustomExen",
            "Cvssiopeia",
            "Cy11",
            "Cyamusite",
            "Cyan_Ryu",
            "cyzz_",
            "da_monster_man",
            "Dachribal",
            "Daigrock",
            "daldott",
            "DalisVibing",
            "Dalrae",
            "Daltom",
            "Dan1k091132_",
            "DangerZone904",
            "darkleonard2",
            "darkskylord69",
            "DaTorchicPlayz",
            "dbsr1975",
            "DeadlyBoop",
            "deathrobloxian",
            "DecentGG",
            "Decxie",
            "Deedogs3742",
            "Deery",
            "Defnd",
            "Demirioo",
            "denisrabbits",
            "denkfout",
            "DerpDurCake",
            "DerpGTX",
            "DerpyFoxPlayz",
            "DesiDucky",
            "DeviHT",
            "dey_dreams",
            "DEYAA__H",
            "DiegoRdz",
            "DiizyMan",
            "Disorded",
            "DivineCreator",
            "Dono1harm",
            "DontTouchMyLife",
            "doodlexy",
            "DoopliGhost_",
            "DoppleDaddy",
            "Dowdens",
            "Dr_Padenski",
            "DrawnUnicorn631",
            "Drea2819",
            "Dream_Wisp",
            "DreChozen",
            "DrEvilsin",
            "dsate3",
            "dtnglo",
            "Duck_lmao",
            "Duckmain",
            "duckyluv",
            "duckynunu",
            "dummymajor",
            "Duzzza",
            "DVNone",
            "dvzixe",
            "Dylpyckles",
            "easyyuser",
            "EBOYCHAR",
            "EdHed",
            "Edweirdo87",
            "eggypoopoo",
            "ein_Holzkopf",
            "ElappoGamingYT",
            "ElDeathly",
            "elijah2589",
            "Elinii",
            "Elite1_1",
            "Elle33Heart",
            "Elyssen",
            "emilol",
            "emilroos",
            "EmilyOoi",
            "EminenceNShadows",
            "EmsMimi",
            "emsytart",
            "Epic_Facha",
            "EricSamme",
            "ermordete",
            "Estrellada",
            "EternalSunbear",
            "Etx_9",
            "Euraiyle",
            "EvelynIsAnOnion",
            "Everlynixx",
            "evesforrealz",
            "EvieBreevy",
            "Exc0mm",
            "Exquillity",
            "Faazazel",
            "fakeminecraftgf",
            "fallbirds",
            "FancyObiWan",
            "FatMonkeyKing",
            "fawries",
            "Fennik_td",
            "FerretStew",
            "fiiini",
            "FinlyKai",
            "Fistopop",
            "Flaccoz",
            "FlashyPig",
            "flcme",
            "Fleaket",
            "fliight",
            "Flindinho",
            "FloraKiiro",
            "flowerchld",
            "foinks",
            "Foolating",
            "Foxily",
            "FOXYIK",
            "fqln",
            "FrankyXXV",
            "Freshavocado0s",
            "FrontDeskLady",
            "Frotsey",
            "Frozenman",
            "Fryla",
            "Funtime333",
            "gabnonymous",
            "Gabs3030",
            "Galistic",
            "Gamdon",
            "GameIvan46",
            "GamingDucky",
            "GamingPrimos131",
            "GangstaAnt",
            "gdst",
            "GEMC",
            "GemeRyZ",
            "GeneralRoute",
            "GhettoHongky",
            "GizmoThePro",
            "glittryvirgo",
            "Goggleko",
            "GoIdenKnight",
            "Goncal0_",
            "GoodIceBear",
            "GOOEYBOY",
            "GoofyGeek",
            "Gooneev",
            "GracefulSlumber",
            "Grenexus",
            "grissemand123",
            "grschrLight",
            "Guon_",
            "H4jper",
            "haewonnnn",
            "Halocakii",
            "hann1ekin",
            "Hansite",
            "HansKinski",
            "Harazi_",
            "haumios",
            "HayIs4Hailey",
            "Haywolf_Gaming",
            "Hazse",
            "Healanii",
            "HeIIedonna",
            "Helmi_C",
            "Hengrove",
            "HeyItsJackM8",
            "highego",
            "HighRefreshRate",
            "HiiFren",
            "HiImCC",
            "HikaOKLM",
            "Hiratina",
            "HobarT551",
            "Hobikage",
            "Honeybee39",
            "honeyplantt",
            "Honeyquill",
            "hoppy819",
            "hotsaucebeats",
            "HouseofJuniper",
            "hpneybee",
            "HuiRen12",
            "HuliLan",
            "Hurcos",
            "Huskel",
            "HydroMC_",
            "HyperrVy",
            "iBeum",
            "IceyMim",
            "iEmmarie",
            "IGiveUpAtNames",
            "ihaltam",
            "iisolarwing",
            "IKTH",
            "illegalPie",
            "im_astepman",
            "Imkayzie",
            "ImKreet",
            "Inaudibley",
            "infernalily",
            "InfinateDreams",
            "Inforcerr",
            "Innaterook",
            "insanesam9",
            "isaacmc12",
            "isislimpinha",
            "Islandish",
            "ItsArrzee",
            "ItsEssieBee",
            "itsmegneko",
            "ItsMusketz",
            "ItsNaira",
            "itsTsunamiCat",
            "ItzzHanVT",
            "IzanitaManzanita",
            "Izice_",
            "izzyisswagger",
            "JackIsAnIdiot",
            "JaJoep",
            "Jakeroly",
            "Jamhalo",
            "jaredy00",
            "JasmineGamez",
            "Jawunleashed",
            "jaykey2227",
            "Jeonar",
            "JerryBS",
            "jessica49",
            "Jholl_",
            "JoesBizzareMama",
            "Jogg",
            "John_Fortnlte",
            "JojoDaYoyo",
            "jojoslices",
            "jojosolos",
            "jontop05",
            "JorgeSword",
            "Joshmanikus",
            "jpd07",
            "jpsm322",
            "JR_Prime",
            "Jsnop",
            "jubileee",
            "Judyloon",
            "Juli3887",
            "JuliYatta",
            "junstrivia",
            "Jupugsa",
            "JustAnzia",
            "JustAthena",
            "JustIcey22",
            "JustJorja",
            "JustLaurita",
            "JustMel65",
            "JustQuacko",
            "KaczeKaczek",
            "kaffnip",
            "KairiNuu",
            "kaleste",
            "kamiiix",
            "kasixna",
            "Kat_the_Chimera",
            "katelynsarahxx",
            "KateyElise021",
            "kaychiru",
            "KayRotz",
            "KayyMC",
            "KhaosKorps",
            "KikuAlayne",
            "kimchiu",
            "KingBayne",
            "Kissinger",
            "kitakyu",
            "Klathulimancer",
            "KokoNataa",
            "komaiii",
            "Koniri",
            "Korulein",
            "kosvy",
            "Kovix254",
            "Kroftmen_x7",
            "Krystalsg",
            "Ksidi",
            "kurohaise",
            "Kusak4be",
            "kyleighcake",
            "kyn4",
            "laalaaleela",
            "Labno",
            "lakeshore5",
            "LaKitty101",
            "LanceWhy",
            "Larbloo",
            "LargoN_Balboa",
            "laura_kill_you",
            "laurvin",
            "LaytzTBE",
            "lazys",
            "LeCheesey",
            "LegHair",
            "LegoFriend",
            "Lessodds",
            "LevelArzt",
            "Levente00____",
            "Lexiliy",
            "leylinka",
            "LGWaffle",
            "Lianam",
            "Lianeu",
            "Libly",
            "LifeIsPatato",
            "lightrocket2",
            "LilHapa",
            "LilithLuvsYa",
            "lillianjl",
            "LinariaMun_Kin",
            "Lingulini",
            "LittlePoohBearr",
            "LizzeMaguire",
            "LlamaSticks",
            "LocutisBorgCube",
            "Loeufys",
            "Lokayy",
            "LolEgirl",
            "LonelyMinotaur7",
            "LooLooPlus",
            "Lopa",
            "LorBuddha",
            "LordBaconNugget",
            "Lordendermen",
            "Lothmeer",
            "LTalk",
            "LuciusTheFoolish",
            "lucyburger",
            "LuhviKyu",
            "LukaTV939",
            "Lumoniam",
            "Lunaoculus",
            "luvcatss",
            "Lynnyxx",
            "LyraTheDrunk",
            "LyteNCrypt",
            "M0SHi_M00",
            "Ma1sy",
            "maenniee",
            "Magetro21",
            "magicsings",
            "MagmaSolo",
            "Mahala_Pink",
            "Maiki_",
            "MajesticFizzi",
            "Malkshake",
            "maltebossking67",
            "manazelfkndazel",
            "Marciismx",
            "Marian_143",
            "Mariitv",
            "Martineli_47",
            "martucarp",
            "MaryIsHere_",
            "Maskyzee",
            "MasterDrey",
            "MasterPaco",
            "MasterYou5",
            "MatchaFoxVR",
            "Mateodlb06",
            "matthew0605",
            "Mavac",
            "Meenuhh",
            "MegaMeat_MC",
            "Megamuncher75",
            "Mel1403",
            "MelBun13",
            "MelissMines",
            "Melizsa",
            "Mermilke",
            "Mewella",
            "MIA0005",
            "MiaTwintania",
            "Microspr",
            "MidnightMadnessx",
            "MidnightZ168",
            "Minegan999",
            "MinerTasha",
            "Minesuklaa",
            "MioRhythm",
            "MissSpacePants",
            "Mitchellangeio",
            "Mitzefy",
            "MiyukiShioru",
            "MiyuNijiiro",
            "Molyhydra",
            "MommMercy",
            "Mommy3240",
            "monkeyzeus1",
            "monstera_fox",
            "Montlwyrm",
            "Mookachuu",
            "MoonBunny30",
            "Mooncakexo",
            "MoonRaySK",
            "mooTMZ",
            "MouseyEnder",
            "mouthfulofmiIk",
            "mp_5_9",
            "MPBilegt",
            "Mr_Rempel",
            "MrMoonLanding",
            "ms_Kats",
            "MS_Mounts",
            "mskedfiend",
            "munchie_obj",
            "munchytaco",
            "Muraneez",
            "Mxcking",
            "MystHartz",
            "Mythical001",
            "MythicLuna",
            "n00laa",
            "NAJT",
            "Namit__",
            "naniodyy",
            "Narnia",
            "Naxilion",
            "naynay954",
            "necialex",
            "Necroncraft",
            "neoning",
            "Neptsune",
            "Nerdgazmic",
            "NgoWay",
            "Niagiri",
            "nicksteel01",
            "Nifrira",
            "NightieYT",
            "Nightshadow1154",
            "ninageee",
            "Nixolay",
            "nocrypa",
            "NoDeal",
            "NolanCat",
            "Nominalgravy",
            "Nopaa",
            "Norbinio",
            "northeness",
            "Northernside",
            "NotDivine_",
            "NotHydra_",
            "NotJano",
            "NotSoSerious2",
            "NotVico",
            "NovaLegacy13",
            "NovumChase",
            "nuggetsgang",
            "nuhzyyyy",
            "Nullus_1",
            "NulPac",
            "nyaukii",
            "NyeBuoi",
            "Nysily",
            "Oblivion7852",
            "Oceana13",
            "oceann8",
            "oGarfield",
            "OGChiknNuggies",
            "Oglittlehippie",
            "OhItsPiper",
            "okbuni",
            "olsbuh",
            "opcl",
            "opvlent",
            "OrangeDogArts",
            "Osymandes",
            "Ouss3409",
            "Owiethe",
            "OwnEight",
            "OxyCGolem",
            "Ozzie750",
            "P1nkyP4nda18",
            "PaoMiaw",
            "Parrm",
            "PassionPig",
            "PastelleShadow",
            "PC_Cycle",
            "Phantrump",
            "phoefi",
            "Pikach_us",
            "PinkieElle",
            "piplupso",
            "pipluVT",
            "pIscript",
            "PixelmonGirl",
            "PlebCS",
            "Plebiain",
            "pohke",
            "Pomcheck",
            "potatopawz",
            "pqlv",
            "predii",
            "PrincessPinecone",
            "prolix",
            "Pseudii",
            "psyomnix",
            "Puffachoo",
            "PunchBag",
            "punkqween",
            "puoz",
            "Purple_T0ast",
            "PurplH0sEr",
            "pyqz",
            "qTaiwan",
            "Qu1nten",
            "QueenEllaBean",
            "Queeni3_",
            "r4yzi",
            "r_3nder",
            "Rabbity11",
            "rahws",
            "rainbowgirl1",
            "Rainnnnnnnnnnnn",
            "rainwet",
            "Ralph",
            "Ramseyer",
            "RatTactics_",
            "RavenYT",
            "realmunyi",
            "Reaper131",
            "Rebecka",
            "Reccoss",
            "RedDuke45",
            "Redlive",
            "regsitaa",
            "ReinFantasy",
            "rezeirl",
            "RhymeTheRapper",
            "RIngenious",
            "RMF1002",
            "RobTipsTV",
            "Rockess",
            "RogueGlitch",
            "Rorachh",
            "RoseDetective",
            "RoseRocketYT",
            "RoseTheGuy",
            "Rotivenos",
            "rotonde",
            "rotsurge",
            "RoyalGoogy",
            "RoyalPear_",
            "Rozwellian",
            "RubbishNotTrash",
            "RubyPlaysTV",
            "rusty_courage",
            "Rvin_Mudbone",
            "Rwssia",
            "RynniBee",
            "RYZZIE_1337",
            "sachizu",
            "Sadaze",
            "Saiikotic",
            "Sajjal_N",
            "salmahayek",
            "Salmonazo",
            "SaltedSalt",
            "Sams",
            "saniixd",
            "SAR0CK",
            "sardina_",
            "SatelliteSelina",
            "schpood",
            "Scorch3103",
            "scotteh",
            "Screenless",
            "Scuttles_225",
            "Sdxxxt_",
            "Sealeh",
            "SeekerOWisdom",
            "Selestialz",
            "SenpaiDejv",
            "Seodrific",
            "serendipityplays",
            "setwria",
            "SexySmokey",
            "Sh1ro_07",
            "Sharese",
            "shayellow",
            "SHeep003",
            "Shekai",
            "shelby395",
            "shimbigail",
            "ShinyPikachu",
            "Shonyx",
            "shotbyalexa",
            "ShoxxyLul",
            "ShrubsRugs",
            "Shxnji",
            "ShyLaBeef",
            "SildenIda",
            "SimpSam",
            "SirEzran",
            "SitzKrieg0",
            "Skeke1",
            "Skucc",
            "sky_dragon_24",
            "SleepySloth_99",
            "sleierslimeHD",
            "Slicknts",
            "SlideSide",
            "Slimey64",
            "SlushieVRC",
            "Snackaroniii",
            "snailpirate",
            "SnapsR",
            "sneakytt",
            "snusu",
            "soda33",
            "SOMANEN",
            "SomeoneNamedEd",
            "SomeSugarBoi",
            "Soniced",
            "Soupports",
            "Sp1cy_cheese",
            "sp33dygonzales77",
            "Spacdogi",
            "Space_Squeakers",
            "Spark2200",
            "Spellcrafter54",
            "SpicyLuna",
            "spiritwolf33333",
            "spookydog_66",
            "spvre",
            "Squ1nz",
            "Squibee",
            "squinkles",
            "Squirtle2021",
            "StanVee",
            "stardustm00n",
            "Starrfrog",
            "StarVeon97",
            "stellarxoxo",
            "StillDreams777",
            "StillnotLilly",
            "stratospherex",
            "Stravilight",
            "StrawberryHolly",
            "Stromoto",
            "Stuffyjoe",
            "Styne",
            "subscribemrbeast",
            "SunsetMagic",
            "Sunwishi",
            "superdudeman75",
            "superduperbro",
            "SuperMango28",
            "superminerJG",
            "supertrooper394",
            "SurgePlugs",
            "SurrenderMaine",
            "SUVIKA",
            "Sweeter67",
            "Swifyz",
            "Swight",
            "swillee",
            "swyshi",
            "Sydnoii",
            "T3Z2",
            "Taasin",
            "Taenyaki",
            "taffyforever",
            "TaneeshaHogan",
            "TangentAbyss674",
            "taxi_zab",
            "tayaruss",
            "taytte",
            "TearsFromV",
            "Terrandth",
            "That_Abyss_Egg",
            "That_Ozzy",
            "Thatderpdoe123",
            "THE_DRIFTER_823",
            "The_Kings_Ghost",
            "TheBigNo",
            "TheCanadianCooki",
            "TheFshy",
            "TheLunarLex",
            "TheNerdyGeek17",
            "TheOneChaira",
            "ThePetrichoral",
            "TheRealVintage",
            "TheRedEclipse",
            "TheSadGuy1",
            "TheSaphy",
            "Timdecoole123",
            "tizzari",
            "toby8889",
            "TofuPrincess",
            "TomatoPaste_",
            "tonedPipes",
            "Tooz_",
            "torinoemi",
            "toroc4t",
            "tortillamonster_",
            "Toxictaco25",
            "toxumi",
            "Trabss",
            "TradeFav444",
            "TrashTripp",
            "TripleLion",
            "TrueAR",
            "TrueRecluse",
            "tsuneluse",
            "Tuppeeyy",
            "TurboPiggyYT",
            "TurnToPaige10",
            "Turret1XD",
            "twistyn0odle",
            "TyrannicalRule",
            "tzd",
            "ubhi",
            "Utiba",
            "vairywings",
            "VanBrickenBrock",
            "vBlaster",
            "Velis_",
            "velvux",
            "Vengeance82",
            "verycuterat",
            "Vevibelle",
            "Vexnzo",
            "Viancyy",
            "vicklerick",
            "Vik_the_Viking",
            "Vilorence",
            "VindicatorFrag",
            "Vinnyzz",
            "VioletFire43",
            "VioletOcean",
            "VirentTarot",
            "Virin1",
            "vorpaelyzis",
            "vvjellyfish",
            "Vyliaa",
            "Waazix19",
            "Wahlund",
            "Warden_InDark",
            "weckman06",
            "WeebyCraft",
            "Weedyoz",
            "werewolfs4lifes",
            "Weseley",
            "whim5y",
            "wiigg1es",
            "WildAgent47",
            "Wilkman",
            "WindyBea",
            "WithMay",
            "WithyCreeps",
            "Wolfscale",
            "WooshuRandom",
            "Wylltea",
            "wynforthewin",
            "x_Lancer_x",
            "x_stormi_x",
            "xAkacjax",
            "XLR8FX",
            "XoSodaXo",
            "XPepperMintx",
            "xPhil79x",
            "xq_a",
            "xTeffax",
            "xxKaYotiC",
            "XXKXNG",
            "XxMakio",
            "xXSaltRatXx",
            "Xymusus",
            "Xziob",
            "Yakibear",
            "Yekoi",
            "ymis",
            "YokaSiri",
            "YokingRice",
            "YouLuckyDuck",
            "Youngson_A",
            "YourStandard",
            "YPTA__",
            "YsSavitar",
            "Yummy_Twinkies",
            "YummyYaamiiii",
            "YunaNanase02",
            "yuskan",
            "YuwenEffie",
            "yviaa",
            "zaroman",
            "zavvygamer",
            "zcani",
            "ZeoliteX",
            "ZER0_7EVEN",
            "ZeSnowy",
            "ZFDfilms",
            "Zigy",
            "Zinclly",
            "zolizo89",
            "zombcal",
            "zombreyy",
            "Zucch1n1Loaf",
            "ZumaTGW",
            "Zurius3",
            "zyephy",
            "F_PP");

    private static final int MAX_FALLBACK_ATTEMPTS = 5;

    public static @NotNull String pickRandomPoolName() {
        return DEFAULT_FALLBACK_ACCOUNT_POOL.get(
                ThreadLocalRandom.current().nextInt(DEFAULT_FALLBACK_ACCOUNT_POOL.size()));
    }

    private final FakePlayerPlugin plugin;
    private final Cache<UUID, PlayerProfile> profileCache =
            CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build();

    public SkinManager(@NotNull FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        clearCache();
        SkinRepository.get().reload();
    }

    public void resolveEffectiveSkin(@NotNull FakePlayer fp, @NotNull Consumer<@Nullable SkinProfile> callback) {

        SkinProfile preferred = getPreferredSkin(fp);
        if (preferred != null && preferred.isValid()) {
            fp.setResolvedSkin(preferred);
            deliver(callback, preferred);
            return;
        }

        SkinProfile alreadyResolved = fp.getResolvedSkin();
        if (alreadyResolved != null && alreadyResolved.isValid()) {
            Config.debugSkin("SkinManager: using already-resolved skin for '"
                    + fp.getName()
                    + "' (source="
                    + alreadyResolved.getSource()
                    + ")");
            deliver(callback, alreadyResolved);
            return;
        }

        String normalizedMode = normalizeMode(Config.skinMode());
        if ("none".equals(normalizedMode)) {
            fp.setResolvedSkin(null);
            deliver(callback, null);
            return;
        }

        SkinProfile cached = getCachedSkinForBot(fp);
        if (cached != null && cached.isValid()) {
            fp.setResolvedSkin(cached);
            deliver(callback, cached);
            return;
        }

        if ("player".equals(normalizedMode)) {

            resolvePlayerModeSkin(fp, callback);
        } else {

            SkinRepository.get().resolve(fp.getSkinName(), skin -> {
                SkinProfile nameTagSkin = getPreferredSkin(fp);
                if (nameTagSkin != null && nameTagSkin.isValid()) {
                    fp.setResolvedSkin(nameTagSkin);
                    persistSkinToDb(fp, nameTagSkin);
                    deliver(callback, nameTagSkin);
                    return;
                }
                fp.setResolvedSkin(skin);
                persistSkinToDb(fp, skin);
                deliver(callback, skin);
            });
        }
    }

    private void resolvePlayerModeSkin(@NotNull FakePlayer fp, @NotNull Consumer<@Nullable SkinProfile> callback) {
        resolvePlayerModeSkin(fp, fp.getSkinName(), 0, new HashSet<>(), callback);
    }

    private void resolvePlayerModeSkin(
            @NotNull FakePlayer fp,
            @NotNull String skinName,
            int fallbackCount,
            @NotNull Set<String> triedNames,
            @NotNull Consumer<@Nullable SkinProfile> callback) {
        triedNames.add(skinName.toLowerCase(Locale.ROOT));

        if (fallbackCount == 0 && plugin.getDatabaseManager() != null) {
            try {
                DatabaseManager.SkinCacheEntry cached =
                        plugin.getDatabaseManager().getCachedSkin(skinName);
                if (cached != null && cached.isValid()) {
                    SkinProfile skin = new SkinProfile(
                            cached.textureValue(), cached.textureSignature(), "db-cache:" + cached.source());
                    fp.setResolvedSkin(skin);
                    Config.debugSkin("SkinManager: using DB-cached skin for '"
                            + skinName
                            + "' (source="
                            + cached.source()
                            + ")");
                    deliver(callback, skin);
                    return;
                }
            } catch (Exception e) {
                Config.debugSkin("SkinManager: DB cache check failed for '" + skinName + "': " + e.getMessage());
            }
        }

        Config.debugSkin("SkinManager: fetching skin for '"
                + skinName
                + "'"
                + (fallbackCount > 0 ? " (fallback attempt #" + fallbackCount + ")" : "")
                + " — bot: '"
                + fp.getName()
                + "'");

        plugin.getSkinFetchService().fetchAsync(skinName, (value, signature) -> {
            SkinProfile nameTagSkin = getPreferredSkin(fp);
            if (nameTagSkin != null && nameTagSkin.isValid()) {
                fp.setResolvedSkin(nameTagSkin);
                deliver(callback, nameTagSkin);
                return;
            }

            SkinProfile existingSkin = fp.getResolvedSkin();
            if (existingSkin != null && existingSkin.isValid()) {
                deliver(callback, existingSkin);
                return;
            }

            if (value != null && !value.isBlank()) {
                SkinProfile skin = new SkinProfile(value, signature, "player:" + skinName);
                fp.setResolvedSkin(skin);

                if (plugin.getDatabaseManager() != null) {
                    plugin.getDatabaseManager().cacheSkin(skinName, value, signature, "mojang:" + skinName);

                    plugin.getDatabaseManager().updateBotSkin(fp.getUuid().toString(), value, signature);
                }

                Config.debugSkin("SkinManager: skin resolved for '"
                        + skinName
                        + "'"
                        + (fallbackCount > 0 ? " (fallback attempt #" + fallbackCount + ")" : ""));
                deliver(callback, skin);
                return;
            }

            if (Config.skinGuaranteed() && fallbackCount < MAX_FALLBACK_ATTEMPTS) {
                String fallbackName = pickRandomFallbackAccountName(triedNames);
                if (fallbackName != null) {
                    Config.debugSkin("SkinManager: no existing skin for '"
                            + skinName
                            + "' on bot '"
                            + fp.getName()
                            + "' — picking random fallback account '"
                            + fallbackName
                            + "' (attempt #"
                            + (fallbackCount + 1)
                            + ").");
                    resolvePlayerModeSkin(fp, fallbackName, fallbackCount + 1, triedNames, callback);
                    return;
                }
            }

            Config.debugSkin("SkinManager: no skin found for '"
                    + skinName
                    + "' on bot '"
                    + fp.getName()
                    + "' — using default skin.");
            fp.setResolvedSkin(null);
            deliver(callback, null);
        });
    }

    private @Nullable String pickRandomFallbackAccountName(@NotNull Set<String> triedNames) {

        List<String> candidates = DEFAULT_FALLBACK_ACCOUNT_POOL.stream()
                .filter(name -> !triedNames.contains(name.toLowerCase(Locale.ROOT)))
                .toList();
        if (!candidates.isEmpty()) {
            String selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            Config.debugSkin("SkinManager: picking random fallback account '"
                    + selected
                    + "' from pool ("
                    + candidates.size()
                    + " candidates remaining).");
            return selected;
        }

        return null;
    }

    private @Nullable SkinProfile extractSkinProfile(@NotNull PlayerProfile profile, @NotNull String sourceName) {
        return profile.getProperties().stream()
                .filter(p -> "textures".equals(p.getName()))
                .findFirst()
                .map(p -> new SkinProfile(p.getValue(), p.getSignature(), "player:" + sourceName))
                .orElse(null);
    }

    public @Nullable SkinProfile getCachedSkinForBot(@NotNull FakePlayer fp) {
        SkinProfile preferred = getPreferredSkin(fp);
        if (preferred != null && preferred.isValid()) {
            return preferred;
        }

        SkinProfile sessionCached = SkinRepository.get().getSessionCached(fp.getSkinName());
        if (sessionCached != null && sessionCached.isValid()) {
            return sessionCached;
        }

        if (!"player".equals(normalizeMode(Config.skinMode()))) {
            return null;
        }

        String skinName = fp.getSkinName();
        String[] cached = plugin.getSkinFetchService().getCached(skinName);
        if (cached != null && cached[0] != null && !cached[0].isBlank()) {
            return new SkinProfile(cached[0], cached[1], "player:" + skinName);
        }

        return null;
    }

    private void tryFallback(@NotNull FakePlayer fp, @NotNull Consumer<@Nullable SkinProfile> callback) {
        if (Config.skinGuaranteed()) {
            SkinRepository.get().getAnyValidSkin(skin -> {
                if (skin != null && skin.isValid()) {
                    fp.setResolvedSkin(skin);
                    persistSkinToDb(fp, skin);
                    deliver(callback, skin);
                } else {
                    Config.debugSkin("SkinManager: guaranteed-skin fallback failed for bot '"
                            + fp.getName()
                            + "' — using default skin (Steve/Alex).");
                    fp.setResolvedSkin(null);
                    deliver(callback, null);
                }
            });
            return;
        }

        Config.debugSkin("SkinManager: no configured fallback skin for bot '"
                + fp.getName()
                + "' — using default skin (Steve/Alex).");
        fp.setResolvedSkin(null);
        deliver(callback, null);
    }

    private void persistSkinToDb(@NotNull FakePlayer fp, @Nullable SkinProfile skin) {
        if (skin == null || !skin.isValid()) return;
        if (plugin.getDatabaseManager() != null) {
            plugin.getDatabaseManager().updateBotSkin(fp.getUuid().toString(), skin.getValue(), skin.getSignature());
        }
    }

    public @Nullable SkinProfile getPreferredSkin(@NotNull FakePlayer fp) {
        SkinProfile resolved = fp.getResolvedSkin();
        if (resolved != null && resolved.isValid() && resolved.getSource().startsWith("nametag:")) {
            return resolved;
        }
        return null;
    }

    public @NotNull CompletableFuture<Boolean> applySkinByPlayerName(
            @NotNull FakePlayer bot, @NotNull String targetPlayerName) {
        if (targetPlayerName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (shouldPreserveNameTagSkin(bot)) {
            return CompletableFuture.completedFuture(false);
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
        if (onlineTarget != null) {
            return applySkinFromPlayer(bot, onlineTarget);
        }

        return applySkinByUsername(bot, targetPlayerName);
    }

    public @NotNull CompletableFuture<Boolean> applySkinByUsername(@NotNull FakePlayer bot, @NotNull String username) {
        if (username.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (shouldPreserveNameTagSkin(bot)) {
            return CompletableFuture.completedFuture(false);
        }

        Player onlineTarget = Bukkit.getPlayerExact(username);
        if (onlineTarget != null) {
            return applySkinFromPlayer(bot, onlineTarget);
        }

        if (plugin.getDatabaseManager() != null) {
            DatabaseManager.SkinCacheEntry cached = plugin.getDatabaseManager().getCachedSkin(username);
            if (cached != null && cached.isValid()) {
                SkinProfile skin = new SkinProfile(
                        cached.textureValue(), cached.textureSignature(), "db-cache:" + cached.source());
                return runOnMainThread(() -> applySkinFromProfile(bot, skin));
            }
        }

        SkinProfile memCached = getCachedSkinForBot(bot);
        if (memCached == null || !memCached.isValid()) {
            String[] fetchCached = plugin.getSkinFetchService().getCached(username);
            if (fetchCached != null && fetchCached[0] != null && !fetchCached[0].isBlank()) {
                SkinProfile skin = new SkinProfile(fetchCached[0], fetchCached[1], "player:" + username);
                return runOnMainThread(() -> applySkinFromProfile(bot, skin));
            }
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String trimmedName = username.trim();
        plugin.getSkinFetchService().fetchAsync(trimmedName, (value, signature) -> {
            if (value == null || value.isBlank()) {
                future.complete(false);
                return;
            }
            SkinProfile skin = new SkinProfile(value, signature, "player:" + trimmedName);
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().cacheSkin(trimmedName, value, signature, "mojang:" + trimmedName);
            }
            runOnMainThread(() -> applySkinFromProfile(bot, skin))
                    .whenComplete((applied, throwable) -> future.complete(Boolean.TRUE.equals(applied)));
        });
        return future;
    }

    public @NotNull CompletableFuture<Boolean> applySkinByUrl(@NotNull FakePlayer bot, @NotNull String url) {
        if (url.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (shouldPreserveNameTagSkin(bot)) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String trimmedUrl = url.trim();
        plugin.getSkinFetchService().fetchByUrl(trimmedUrl, (value, signature) -> {
            if (value == null || value.isBlank()) {
                future.complete(false);
                return;
            }
            runOnMainThread(() -> {
                        if (shouldPreserveNameTagSkin(bot)) return false;
                        Player botPlayer = bot.getPlayer();
                        if (botPlayer == null || !botPlayer.isOnline()) return false;
                        return applySkinFromProfile(bot, new SkinProfile(value, signature, "url:" + trimmedUrl));
                    })
                    .whenComplete((applied, throwable) -> future.complete(Boolean.TRUE.equals(applied)));
        });
        return future;
    }

    public @NotNull CompletableFuture<Boolean> applySkinFromPlayer(@NotNull FakePlayer bot, @NotNull Player from) {
        if (shouldPreserveNameTagSkin(bot)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerProfile sourceProfile = from.getPlayerProfile();
        SkinProfile sourceSkin = skinFromProfile(sourceProfile, "player:" + from.getName());
        if (sourceSkin == null || !sourceSkin.isValid()) {
            return CompletableFuture.completedFuture(false);
        }

        return runOnMainThread(() -> {
            Player botPlayer = bot.getPlayer();
            if (botPlayer == null || !botPlayer.isOnline()) return false;
            copyTexture(sourceProfile, botPlayer);
            bot.setResolvedSkin(sourceSkin);
            persistSkinToDb(bot, sourceSkin);
            refreshTabListSkin(bot);
            return true;
        });
    }

    public @NotNull CompletableFuture<Boolean> applySkinFromOfflinePlayer(
            @NotNull FakePlayer bot, @NotNull OfflinePlayer from) {
        if (shouldPreserveNameTagSkin(bot)) {
            return CompletableFuture.completedFuture(false);
        }

        PlayerProfile sourceProfile = from.getPlayerProfile();
        if (sourceProfile.hasTextures()) {
            SkinProfile sourceSkin = skinFromProfile(
                    sourceProfile,
                    "offline:"
                            + Optional.ofNullable(from.getName())
                                    .orElse(from.getUniqueId().toString()));
            if (sourceSkin == null || !sourceSkin.isValid()) return CompletableFuture.completedFuture(false);
            return runOnMainThread(() -> {
                Player botPlayer = bot.getPlayer();
                if (botPlayer == null || !botPlayer.isOnline()) return false;
                copyTexture(sourceProfile, botPlayer);
                bot.setResolvedSkin(sourceSkin);
                persistSkinToDb(bot, sourceSkin);
                refreshTabListSkin(bot);
                return true;
            });
        }

        PlayerProfile cached = profileCache.getIfPresent(from.getUniqueId());
        if (cached != null && cached.hasTextures()) {
            SkinProfile cachedSkin = skinFromProfile(
                    cached,
                    "offline-cache:"
                            + Optional.ofNullable(from.getName())
                                    .orElse(from.getUniqueId().toString()));
            if (cachedSkin == null || !cachedSkin.isValid()) return CompletableFuture.completedFuture(false);
            return runOnMainThread(() -> {
                Player botPlayer = bot.getPlayer();
                if (botPlayer == null || !botPlayer.isOnline()) return false;
                copyTexture(cached, botPlayer);
                bot.setResolvedSkin(cachedSkin);
                persistSkinToDb(bot, cachedSkin);
                refreshTabListSkin(bot);
                return true;
            });
        }

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        boolean completed = sourceProfile.complete();
                        if (completed && sourceProfile.hasTextures()) {
                            profileCache.put(from.getUniqueId(), sourceProfile);
                            return ProfileCompleteResult.SUCCESS;
                        }
                        return ProfileCompleteResult.FAILED;
                    } catch (Exception e) {
                        Config.debugSkin("SkinManager: failed to fetch online profile from Mojang"
                                + " for "
                                + Optional.ofNullable(from.getName())
                                        .orElse(from.getUniqueId().toString())
                                + ": "
                                + e.getMessage());
                        return ProfileCompleteResult.ERROR;
                    }
                })
                .thenCompose(result -> {
                    if (result != ProfileCompleteResult.SUCCESS) {
                        if (result == ProfileCompleteResult.FAILED) {
                            Config.debugSkin("SkinManager: failed to fetch online skin for "
                                    + Optional.ofNullable(from.getName())
                                            .orElse(from.getUniqueId().toString())
                                    + " - player may not exist");
                        }
                        return CompletableFuture.completedFuture(false);
                    }
                    return runOnMainThread(() -> {
                        if (shouldPreserveNameTagSkin(bot)) return false;
                        Player botPlayer = bot.getPlayer();
                        if (botPlayer == null || !botPlayer.isOnline()) return false;
                        SkinProfile fetchedSkin = skinFromProfile(
                                sourceProfile,
                                "offline:"
                                        + Optional.ofNullable(from.getName())
                                                .orElse(from.getUniqueId().toString()));
                        if (fetchedSkin == null || !fetchedSkin.isValid()) return false;
                        copyTexture(sourceProfile, botPlayer);
                        bot.setResolvedSkin(fetchedSkin);
                        NmsPlayerSpawner.applySkinToGameProfile(botPlayer, fetchedSkin);
                        persistSkinToDb(bot, fetchedSkin);
                        refreshTabListSkin(bot);
                        return true;
                    });
                });
    }

    public boolean applySkinFromProfile(@NotNull FakePlayer bot, @Nullable SkinProfile skin) {
        if (skin == null || !skin.isValid()) return false;
        if (shouldPreserveNameTagSkin(bot) && !skin.getSource().startsWith("nametag:")) return false;
        boolean applied = applySkinFromTextures(bot, skin.getValue(), skin.getSignature());
        if (applied) {
            bot.setResolvedSkin(skin);
        }
        return applied;
    }

    public boolean applySkinFromTextures(@NotNull FakePlayer bot, @NotNull String texture, @Nullable String signature) {
        if (texture.isBlank()) return false;
        Player botPlayer = bot.getPlayer();
        if (botPlayer == null || !botPlayer.isOnline()) return false;

        if (NmsPlayerSpawner.isFoliaServer()) {
            SkinProfile appliedSkin = new SkinProfile(texture, signature, "direct:" + bot.getName());
            bot.setResolvedSkin(appliedSkin);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, appliedSkin);
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), texture, signature);
            }
            return true;
        }

        try {
            PlayerProfile profile = botPlayer.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", texture, signature != null ? signature : ""));
            botPlayer.setPlayerProfile(profile);
            SkinProfile appliedSkin = new SkinProfile(texture, signature, "direct:" + bot.getName());
            bot.setResolvedSkin(appliedSkin);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, appliedSkin);

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), texture, signature);
            }

            FakePlayerManager mgr = plugin.getFakePlayerManager();
            if (mgr != null) {
                mgr.refreshSkinForAll(bot);
            }
            return true;
        } catch (Exception e) {
            FppLogger.warn("SkinManager: failed to apply texture skin to " + bot.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean resetToDefaultSkin(@NotNull FakePlayer bot) {
        Player botPlayer = bot.getPlayer();
        if (botPlayer == null || !botPlayer.isOnline()) return false;

        if (NmsPlayerSpawner.isFoliaServer()) {
            bot.setResolvedSkin(null);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, null);
            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), null, null);
            }
            return true;
        }

        try {
            PlayerProfile profile = botPlayer.getPlayerProfile();
            profile.removeProperty("textures");
            profile.clearProperties();
            botPlayer.setPlayerProfile(profile);
            bot.setResolvedSkin(null);
            NmsPlayerSpawner.applySkinToGameProfile(botPlayer, null);

            if (plugin.getDatabaseManager() != null) {
                plugin.getDatabaseManager().updateBotSkin(bot.getUuid().toString(), null, null);
            }

            FakePlayerManager mgr = plugin.getFakePlayerManager();
            if (mgr != null) {
                mgr.refreshSkinForAll(bot);
            }
            return true;
        } catch (Exception e) {
            FppLogger.warn("SkinManager: failed to reset skin for " + bot.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public @Nullable SkinProfile getSkinForBot(@NotNull FakePlayer bot) {
        SkinProfile preferred = getPreferredSkin(bot);
        if (preferred != null && preferred.isValid()) return preferred;

        Player botPlayer = bot.getPlayer();
        if (botPlayer == null || !botPlayer.isOnline()) {
            return bot.getResolvedSkin();
        }

        PlayerProfile profile = botPlayer.getPlayerProfile();
        if (!profile.hasTextures()) {
            return bot.getResolvedSkin();
        }

        Optional<ProfileProperty> texturesProp = profile.getProperties().stream()
                .filter(p -> "textures".equals(p.getName()))
                .findFirst();
        if (texturesProp.isEmpty()) {
            return bot.getResolvedSkin();
        }

        ProfileProperty prop = texturesProp.get();
        return new SkinProfile(prop.getValue(), prop.getSignature(), "current:" + bot.getName());
    }

    public @NotNull CompletableFuture<Boolean> preloadSkin(@NotNull String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        PlayerProfile profile = offlinePlayer.getPlayerProfile();

        if (profile.hasTextures()) {
            profileCache.put(offlinePlayer.getUniqueId(), profile);
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean completed = profile.complete();
                if (completed && profile.hasTextures()) {
                    profileCache.put(offlinePlayer.getUniqueId(), profile);
                    return true;
                }
            } catch (Exception e) {
                FppLogger.debug("SkinManager: failed to preload skin for " + playerName + ": " + e.getMessage());
            }
            return false;
        });
    }

    public void clearCache() {
        profileCache.invalidateAll();
        plugin.getSkinFetchService().clearCache();
    }

    public long getCacheSize() {
        return profileCache.size();
    }

    private boolean shouldPreserveNameTagSkin(FakePlayer bot) {
        return getPreferredSkin(bot) != null;
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "player";
        return switch (mode.trim().toLowerCase()) {
            case "off", "disabled", "none" -> "none";
            case "custom", "random" -> "random";
            case "auto", "player" -> "player";
            default -> mode.trim().toLowerCase();
        };
    }

    private void copyTexture(@NotNull PlayerProfile from, @NotNull Player to) {
        if (NmsPlayerSpawner.isFoliaServer()) {
            from.getProperties().stream()
                    .filter(p -> "textures".equals(p.getName()))
                    .findAny()
                    .map(p -> new SkinProfile(p.getValue(), p.getSignature(), "profile:" + to.getName()))
                    .ifPresent(skin -> NmsPlayerSpawner.applySkinToGameProfile(to, skin));
            return;
        }
        PlayerProfile toProfile = to.getPlayerProfile();
        toProfile.setTextures(from.getTextures());
        from.getProperties().stream()
                .filter(p -> "textures".equals(p.getName()))
                .findAny()
                .ifPresent(toProfile::setProperty);
        to.setPlayerProfile(toProfile);
        try {
            NmsPlayerSpawner.forceAllSkinParts(to);
        } catch (Exception e) {
            FppLogger.debug("SkinManager: could not force skin parts: " + e.getMessage());
        }
    }

    private @Nullable SkinProfile skinFromProfile(@NotNull PlayerProfile profile, @NotNull String source) {
        Optional<ProfileProperty> textures = profile.getProperties().stream()
                .filter(p -> "textures".equals(p.getName()))
                .findFirst();
        if (textures.isEmpty()) return null;
        ProfileProperty property = textures.get();
        if (property.getValue() == null || property.getValue().isBlank()) return null;
        return new SkinProfile(property.getValue(), property.getSignature(), source);
    }

    void refreshTabListSkin(@NotNull FakePlayer bot) {
        FakePlayerManager mgr = plugin.getFakePlayerManager();
        if (mgr != null) {
            mgr.refreshSkinForAll(bot);
        }
    }

    private @NotNull CompletableFuture<Boolean> runOnMainThread(Callable<Boolean> action) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FppScheduler.runSync(plugin, () -> {
            try {
                future.complete(action.call());
            } catch (Exception e) {
                future.complete(false);
            }
        });
        return future;
    }

    private void deliver(Consumer<@Nullable SkinProfile> callback, @Nullable SkinProfile profile) {
        if (Bukkit.isPrimaryThread()) {
            callback.accept(profile);
            return;
        }
        FppScheduler.runSync(plugin, () -> callback.accept(profile));
    }

    private enum ProfileCompleteResult {
        SUCCESS,
        FAILED,
        ERROR
    }
}
