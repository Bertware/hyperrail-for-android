package be.bertmarcelis.thesis.test;

import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.Request;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;

import be.bertmarcelis.thesis.irail.contracts.IRailErrorResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IRailSuccessResponseListener;
import be.bertmarcelis.thesis.irail.contracts.IrailDataProvider;
import be.bertmarcelis.thesis.irail.implementation.Lc2IrailApi;
import be.bertmarcelis.thesis.irail.implementation.LinkedConnectionsApi;
import be.bertmarcelis.thesis.irail.implementation.Vehicle;
import be.bertmarcelis.thesis.irail.implementation.linkedconnections.LinkedConnections;
import be.bertmarcelis.thesis.irail.implementation.linkedconnections.LinkedConnectionsProvider;
import be.bertmarcelis.thesis.irail.implementation.requests.IrailVehicleRequest;

import static java.lang.Runtime.getRuntime;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

@RunWith(AndroidJUnit4.class)
public class TrainIndexBenchmark implements IRailErrorResponseListener, IRailSuccessResponseListener<Vehicle> {

    HashMap<String, Long> start;
    HashMap<String, Long> end;
    ArrayList<String> done;
    private volatile boolean free = true;

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    @Test
    public void benchmark() {
        final String[] trains = new String[]{"IC2604", "IC4003", "IC527", "IC504", "IC1903", "IC4325", "L5275", "IC2303", "IC4004", "IC1826", "S102076", "IC3105", "IC727", "IC3704", "IC3628", "IC1504", "IC3305", "IC2126", "L855", "IC3705", "IC528", "L6077", "IC3726", "L876", "IC106", "IC2605", "IC2626", "IC4326", "IC705", "IC3629", "IC2227", "IC2804", "IC3327", "IC2205", "IC1927", "IC505", "IC4027", "IC1905", "IC9211", "IC2526", "S11776", "L2555", "IC2904", "L5055", "IC1904", "IC9212", "IC3126", "S102055", "L6055", "S23755", "S85676", "IC2506", "L5256", "L6256", "S11956", "L5276", "L1876", "IC2304", "S102077", "IC4005", "IC1827", "L877", "S11756", "IC3106", "IC728", "IC3127", "IC4306", "IC3827", "L4977", "IC1806", "S11976", "IC1505", "IC2928", "L2877", "IC3306", "L2576", "S23777", "IC3806", "IC4206", "IC3604", "IC2329", "IC3829", "L4756", "IC3805", "IC2505", "IC2527", "IC2927", "IC2127", "S86577", "L4555", "L856", "IC3706", "L5027", "L5756", "S23756", "L5977", "S203056", "IC529", "IC4327", "IC2103", "IC1529", "IC3828", "IC2906", "IC3727", "IC2826", "IC3804", "IC2106", "L556", "L5081", "L2577", "IC2128", "IC827", "IC2606", "L4856", "IC503", "IC2627", "S61577", "IC706", "IC2628", "IC2129", "L4376", "L6156", "IC3630", "L10555", "L3927", "IC107", "IC2228", "IC2805", "IC3328", "IC2206", "IC1928", "L4956", "L2777", "IC506", "IC4028", "L656", "IC1906", "IC9215", "L2856", "L577", "IC2306", "S86556", "S11777", "L677", "L2556", "IC2905", "L5056", "S85656", "IC3606", "IC1527", "L4578", "L4677", "IC4127", "S61556", "L6177", "S102056", "S203077", "IC2828", "IC2328", "L4877", "IC2806", "S85677", "IC2507", "L5877", "L5257", "L4777", "L1857", "S11957", "L5277", "L1877", "IC2305", "IC4006", "S102078", "IC1828", "L878", "S103857", "S11757", "IC3107", "IC2807", "L2557", "IC4406", "L5777", "L6277", "IC1530", "IC729", "L5177", "L757", "IC3128", "IC4307", "L5357", "IC1807", "S11977", "IC1506", "IC2929", "L778", "L2878", "IC3307", "S23778", "L5157", "IC3807", "IC4207", "IC3605", "IC2330", "IC3830", "L6079", "IC2229", "IC2528", "IC109", "IC530", "S86578", "L5857", "L857", "IC3707", "L5028", "IC4328", "S86357", "S23757", "IC9216", "L4557", "S203057", "L4878", "IC2104", "IC2907", "L5579", "IC3728", "IC2827", "S86377", "IC2107", "L557", "IC12108", "L4278", "L5082", "L2578", "S61579", "IC2607", "IC4107", "L4857", "S61578", "IC707", "L4657", "IC2130", "IC3631", "L10556", "IC807", "IC3329", "IC2207", "L5006", "L2757", "IC1929", "L2778", "IC507", "IC4029", "L657", "IC1907", "IC9219", "L2857", "S53357", "L5557", "L578", "IC2307", "L5007", "S86557", "S11778", "L678", "L5057", "S85657", "L4257", "L4457", "IC3607", "IC4228", "IC1528", "L679", "L4678", "IC1507", "IC4128", "S61557", "L5957", "S102057", "S203078", "L6057", "IC2829", "S85678", "IC2508", "L5258", "L1858", "L6258", "S11958", "L5278", "L1878", "IC4007", "S102079", "IC1829", "S103878", "S53379", "L558", "L879", "S103858", "S11758", "IC3108", "IC2808", "L2558", "IC1531", "IC730", "L5178", "IC4429", "IC3129", "IC4308", "L4979", "L5358", "IC1808", "S11978", "IC2930", "L6158", "L2879", "IC3308", "S23779", "L5158", "IC3808", "L5029", "IC4208", "IC19708", "IC2331", "IC3831", "L4758", "IC2230", "L3908", "IC2529", "IC531", "S86579", "L5378", "L858", "IC3708", "IC4329", "S86358", "L5758", "S23758", "IC9220", "L5979", "S203058", "L4879", "L4358", "IC2105", "IC2908", "IC3729", "S86378", "IC2108", "L5083", "L2579", "IC829", "S61580", "IC19916", "IC2608", "IC4108", "L4858", "IC2629", "IC708", "L4658", "IC2630", "IC2131", "L4378", "IC3632", "L10557", "L3929", "IC3330", "IC2208", "L2758", "IC1930", "L4958", "L2779", "IC508", "IC4030", "L658", "IC1908", "IC9223", "L2858", "S53358", "L4479", "L579", "IC2308", "L5008", "S86558", "S11779", "IC19913", "L5058", "ICT6959", "S85658", "IC3608", "IC4229", "L4580", "L680", "L4679", "IC1508", "IC4129", "S61558", "L6179", "S102058", "S203079", "IC2830", "S85679", "IC2509", "L5879", "L5259", "L4779", "L1859", "S11959", "L5279", "L1879", "IC4008", "S102080", "IC1830", "S103879", "S53380", "L880", "S103859", "S11759", "IC3109", "IC2809", "L2559", "IC4408", "L5779", "L6279", "IC1532", "IC731", "L5179", "IC19707", "L759", "IC3130", "IC4309", "L5359", "IC1809", "S11979", "IC2931", "L780", "L2880", "IC3309", "S23780", "L5159", "IC3809", "L5030", "IC4209", "IC19710", "IC2332", "IC3832", "L6081", "IC2231", "IC2530", "IC111", "IC532", "ICT6960", "S86580", "L5379", "L5859", "L859", "IC3709", "IC4330", "S86359", "S23759", "IC9224", "L4559", "S203059", "L4880", "IC108", "IC2909", "IC3730", "S86379", "IC2109", "L559", "IC110", "L4280", "L5084", "L2580", "S61581", "IC2609", "IC19920", "IC4109", "L4859", "L5581", "IC709", "L4659", "IC2132", "IC3633", "L10558", "IC809", "IC3331", "IC2209", "ICT6909", "L2759", "IC1931", "L2780", "IC509", "IC4031", "L659", "IC1909", "IC9227", "L2859", "S53359", "L5559", "L580", "IC2309", "L5009", "S86559", "S11780", "IC19917", "L5059", "S85659", "L4259", "L4459", "IC3609", "IC4230", "L681", "L4680", "IC1509", "IC4130", "S61559", "L5959", "S102059", "S203080", "L6059", "IC2831", "IZY9602", "S85680", "IC2510", "L5260", "L1860", "L6260", "S11960", "L5280", "L1880", "IC4009", "S102081", "IC1831", "S103880", "S53381", "L560", "L881", "S103860", "S11760", "IC3110", "IC2810", "L2560", "IC1533", "IC732", "L5180", "IC19709", "IC4431", "IC3131", "IC4310", "L4981", "L5360", "IC1810", "S11980", "IC2932", "L6160", "L2881", "IC3310", "S23781", "L5160", "IC3810", "L5031", "IC4210", "IC19712", "IC2333", "IC3833", "L4760", "IC2232", "L3910", "IC2531", "IC533", "ICT6961", "S86581", "L5380", "L860", "IC3710", "IC4331", "S86360", "L5760", "S23760", "L5981", "S203060", "L4881", "L4360", "IC2910", "IC3731", "S86380", "IC2110", "L5085", "L2581", "IC831", "S61582", "IC19926", "IC2610", "IC4110", "L4860", "IC2631", "IC710", "L4660", "IC2632", "IC2133", "L4380", "IC3634", "L10559", "L3931", "ICT6910", "IC3332", "IC2210", "ICT6964", "IC9228", "L2760", "IC1932", "L4960", "L2781", "IC510", "S11781", "IC4032", "L660", "IC1910", "IC9231", "L2860", "S53360", "L4481", "L581", "IC2310", "L5010", "S86560", "IC19921", "L5060", "S85660", "IC3610", "IC4231", "L4582", "L682", "L4681", "IC1510", "IC4131", "S61560", "L6181", "S102060", "S203081", "IC2832", "S85681", "IC2511", "L5881", "L5261", "L4781", "L1861", "S11961", "L5281", "L1881", "IC4010", "S102082", "IC1832", "S103881", "S53382", "L882", "S103861", "S11761", "IC3111", "IC2811", "L2561", "IC4410", "L5781", "L6281", "IC1534", "IC733", "L5181", "IC19713", "L761", "IC3132", "IC4311", "L5361", "IC1811", "S11981", "IC2933", "L782", "L2882", "IC3311", "S23782", "L5161", "IC3811", "L5032", "IC4211", "IC19714", "IC2334", "IC3834", "L6083", "IC2233", "IC2532", "IC113", "IC534", "S86582", "L5381", "L5861", "L861", "IC3711", "IC4332", "S86361", "S23761", "IC9232", "L4561", "S203061", "L4882", "IC2911", "IC3732", "S86381", "IC2111", "L561", "IC112", "L4282", "L5086", "L2582", "S61583", "IC2611", "IC19930", "IC4111", "L4861", "L5583", "IC711", "L4661", "IC2134", "IC3635", "L10560", "IC811", "IC3333", "IC2211", "L2761", "IC1933", "L2782", "IC511", "IC4033", "L661", "IC1911", "IC9235", "L2861", "S53361", "L5561", "L582", "IC2311", "L5011", "S86561", "S11782", "IC19925", "L5061", "S85661", "L4261", "L4461", "IC3611", "IC4232", "L683", "L4682", "IC1511", "IC4132", "S61561", "L5961", "S102061", "S203082", "L6061", "IC2833", "S85682", "IC2512", "L5262", "L4737", "L1862", "L6262", "S11962", "L5282", "L1882", "IC4011", "S102083", "IC1833", "S103882", "S53383", "L562", "L883", "S103862", "S11762", "IC3112", "IC2812", "L2562", "IC1535", "IC19717", "IC734", "L5182", "IC4433", "IC3133", "IC4312", "L4983", "L5362", "IC1812", "S11982", "IC2934", "L6162", "L2883", "IC3312", "S23783", "L5162", "IC3812", "L5033", "IC4212", "IC19734", "IC2335", "IC3835", "L4762", "IC2234", "L3912", "IC2533", "IC535", "S86583", "L5382", "L862", "IC3712", "L4712", "IC4333", "S86362", "L5762", "S23762", "IC9236", "L5983", "S203062", "L4883", "L4362", "IC2912", "IC3733", "S86382", "IC2112", "L5087", "L2583", "IC833", "S61584", "IC19936", "IC2612", "IC4112", "L4862", "IC2633", "IC712", "L4662", "IC2634", "IC2135", "L4382", "IC3636", "L10561", "L3933", "IC3334", "IC2212", "L2762", "IC1934", "L4962", "L2783", "IC512", "IC4034", "L662", "IC1912", "IC9239", "L2862", "S53362", "L4483", "L583", "IC2312", "L5012", "S86562", "S11783", "IC19931", "L5062", "S85662", "IC3612", "IC4233", "L4584", "L684", "L4683", "IC1512", "IC4133", "S61562", "L6183", "S102062", "S203083", "IC2834", "S85683", "IC2513", "L5883", "L5263", "L4783", "L4738", "L1863", "S11963", "L5283", "L1883", "IC4012", "S102084", "IC1834", "S103883", "S53384", "L884", "S103863", "S11763", "IC3113", "IC2813", "L2563", "IC4412", "L5783", "L6283", "IC1536", "IC735", "L5183", "IC19745", "L763", "IC3134", "IC4313", "L5363", "IC1813", "S11983", "IC2935", "L784", "L2884", "IC3313", "S23784", "L5163", "IC3813", "L5034", "IC4213", "IC19732", "IC2336", "IC3836", "L6085", "IC2235", "IC2534", "IC115", "IC536", "S86584", "L5383", "L5863", "L863", "IC3713", "L4713", "IC4334", "S86363", "S23763", "IC9240", "L4563", "S203063", "L4884", "IC2913", "IC3734", "S86383", "IC2113", "L563", "IC114", "L4284", "L5088", "L2584", "S61585", "IC2613", "IC19942", "IC4113", "L4863", "L5585", "IC713", "L4663", "IC2136", "IC3637", "L10562", "IC813", "IC3335", "IC2213", "L2763", "IC1935", "L2784", "IC513", "IC4035", "L663", "IC1913", "IC9243", "L2863", "S53363", "L5563", "L584", "IC2313", "L5013", "S86563", "S11784", "IC19937", "L5063", "S85663", "L4263", "L4463", "IC3613", "IC4234", "L685", "L4684", "IC1513", "IC4134", "S61563", "L5963", "S102063", "S203084", "L6063", "IC2835", "S85684", "IC2514", "IC19721", "L5264", "L4739", "L1864", "L6264", "S11964", "L5284", "L1884", "IC4013", "S102085", "IC1835", "S103884", "S53385", "L564", "L885", "S103864", "S11764", "IC3114", "IC2814", "L2564", "IC1537", "IC736", "L5184", "IC4435", "IC3135", "IC4314", "L4985", "L5364", "IC1814", "S11984", "IC2936", "L6164", "L2885", "IC3314", "S23785", "L5164", "IC3814", "L5035", "IC4214", "IC19718", "IC2337", "IC3837", "L4764", "IC2236", "L3914", "IC2535", "IC537", "S86585", "L5384", "L864", "IC3714", "L4714", "IC4335", "S86364", "L5764", "S23764", "L5985", "S203064", "L4885", "L4364", "IC2914", "IC3735", "S86384", "IC2114", "L5089", "L2585", "IC835", "S61586", "IC19944", "IC2614", "IC4114", "L4864", "IC2635", "IC714", "L4664", "IC2636", "IC2137", "L4384", "IC3638", "L10563", "L3935", "IC3336", "IC2214", "IC9244", "L2764", "IC1936", "L4964", "L2785", "IC514", "IC4036", "L664", "IC1914", "IC9247", "L2864", "S53364", "L4485", "L585", "IC2314", "L5014", "S86564", "S11785", "IC19939", "L5064", "S85664", "IC3614", "IC4235", "L4586", "L686", "L4685", "IC1514", "IC4135", "S61564", "L6185", "S102064", "S203085", "IC2836", "S85685", "IC2515", "L5885", "L5265", "L4785", "L4740", "L1865", "S11965", "L5285", "L1885", "IC4014", "S102086", "IC1836", "S103885", "S53386", "L886", "S103865", "S11765", "IC3115", "IC2815", "L2565", "IC4414", "L5785", "L6285", "IC1538", "IC737", "L5185", "IC19723", "L765", "IC3136", "IC4315", "L5365", "IC1815", "S11985", "IC2937", "L786", "L2886", "IC3315", "S23786", "L5165", "IC3815", "L5036", "IC4215", "IC19720", "IC2338", "IC3838", "L6087", "IC2237", "IC2536", "IC117", "IC538", "S86586", "L5385", "L5865", "L865", "IC3715", "L4715", "IC4336", "S86365", "S23765", "IC9248", "L4565", "S203065", "L4886", "IC2915", "IC3736", "S86385", "IC2115", "L565", "IC116", "L4286", "L5090", "L2586", "S61587", "IC2615", "IC19950", "IC4115", "L4865", "L5587", "IC715", "L4665", "IC2138", "IC3639", "L10564", "IC815", "IC3337", "IC2215", "L2765", "IC1937", "L2786", "IC515", "IC4037", "L665", "IC1915", "IC9251", "L2865", "S53365", "L5565", "L586", "IC2315", "L5015", "S86565", "S11786", "IC19943", "L5065", "S85665", "L4265", "L4465", "IC3615", "IC4236", "L687", "L4686", "IC1515", "IC4136", "S61565", "L5965", "S102065", "S203086", "L6065", "IC2837", "S85686", "IC2516", "L5266", "L4741", "L1866", "L6266", "S11966", "L5286", "L1886", "IC4015", "S102087", "IC1837", "S103886", "S53387", "L566", "L887", "S103866", "S11766", "IC3116", "IC2816", "L2566", "IC1539", "IC738", "L5186", "IC19725", "IC4437", "IC3137", "IC4316", "L4987", "L5366", "IC1816", "S11986", "IC2938", "L6166", "L2887", "IC3316", "S23787", "L5166", "IC3816", "L5037", "IC4216", "IC19738", "IC2339", "IC3839", "L4766", "IC2238", "L3916", "IC2537", "IC539", "S86587", "L5386", "L866", "IC3716", "L4716", "IC4337", "S86366", "L5766", "S23766", "IC9252", "L5987", "S203066", "L4887", "L4366", "IC4613", "IZY9606", "IC2916", "IC3737", "S86386", "IC2116", "L5091", "L2587", "IC837", "S61588", "IC19954", "IC2616", "IC4116", "L4866", "IC2637", "IC716", "L4666", "IC2638", "IC2139", "L4386", "IC3640", "L10565", "L3937", "IC3338", "IC2216", "L2766", "IC1938", "L4966", "L2787", "IC516", "IC4038", "L666", "IC1916", "IC9255", "L2866", "S53366", "L4487", "L587", "IC2316", "L5016", "S86566", "S11787", "IC19949", "L5066", "S85666", "IC3616", "IC4237", "L4588", "L688", "L4687", "IC1516", "ICT6937", "IC4137", "S61566", "L6187", "S102066", "S203087", "IC2838", "S85687", "IC2517", "L5887", "L5267", "L4787", "L4742", "L1867", "S11967", "L5287", "L1887", "IC4016", "S102088", "IC1838", "S103887", "S53388", "L888", "S103867", "S11767", "IC3117", "IC2817", "L2567", "IC4416", "L5787", "L6287", "IC1540", "IC739", "L5187", "L767", "IC19747", "IC3138", "IC4317", "L5367", "IC1817", "S11987", "ICT6967", "IC2939", "L788", "L2888", "IC3317", "S23788", "L5167", "IC3817", "L5038", "IC4217", "IC19722", "IC2340", "IC3840", "L6089", "IC2239", "IC2538", "IC119", "IC540", "S86588", "L5387", "L5867", "L867", "IC3717", "L4717", "IC4338", "S86367", "S23767", "IC9256", "L4567", "S203067", "L4888", "IC2917", "IC3738", "S86387", "IC2117", "L567", "IC118", "L4288", "L5092", "L2588", "S61589", "IC2617", "IC19960", "IC4117", "L4867", "L5589", "IC717", "L4667", "IC2140", "IC3641", "L10566", "IC817", "IC3339", "IC2217", "L2767", "IC1939", "L2788", "IC517", "IC4039", "L667", "IC1917", "IC9259", "L2867", "S53367", "L5567", "L588", "IC2317", "L5017", "ICT6984", "S86567", "S11788", "IC19957", "L5067", "S85667", "L4267", "L4467", "IC3617", "IC4238", "L689", "L4688", "IC1517", "ICT6938", "IC4138", "S61567", "L5967", "ICT6985", "S102067", "S203088", "L6067", "IC2839", "S85688", "IC2518", "IC19733", "L5268", "L4743", "L1868", "L6268", "S11968", "L5288", "L1888", "S102089", "IC4017", "IC1839", "S103888", "S53389", "L568", "L889", "S103868", "S11768", "IC3118", "IC2818", "L2568", "IC1541", "IC740", "L5188", "IC4439", "IC3139", "IC4318", "L6168", "L4989", "L5368", "IC1818", "S11988", "ICT6986", "IC2940", "L2889", "IC3318", "S23789", "L5168", "IC3818", "L5039", "IC4218", "IC19724", "IC2341", "IC3841", "L4768", "IC2240", "L3918", "IC2539", "IC541", "S86589", "L5388", "L868", "IC3718", "L4718", "IC4339", "S86368", "S23768", "L5989", "L5768", "S203068", "L4889", "L4368", "IC2918", "IC3739", "S86388", "IC2118", "L5093", "L2589", "IC839", "S61590", "IC19964", "IC2618", "IC4118", "L4868", "IC2639", "IC718", "L4668", "IC2640", "IC2141", "L4388", "IC3642", "L10567", "L3939", "IC3340", "IC2218", "IC9260", "L2768", "IC1940", "L4968", "L2789", "IC518", "S11789", "IC4040", "L668", "IC1918", "IC9263", "L2868", "S53368", "L4489", "L589", "IC2318", "L5018", "S86568", "IC19961", "L5068", "S85668", "IC3618", "IC4239", "L4590", "L690", "L4689", "IC1518", "ICT6939", "IC4139", "S61568", "L6189", "ICT6987", "S102068", "S203089", "IC2840", "S85689", "IC2519", "L5269", "L4789", "L4744", "L1869", "S11969", "L5289", "L1889", "IC4018", "S102090", "IC1840", "S103889", "S53390", "L890", "S103869", "S11769", "IC3119", "IC2819", "L2569", "IC4418", "L5789", "L6289", "IC1542", "IC741", "L5189", "IC19735", "L769", "IC3140", "IC4319", "L5369", "IC1819", "S11989", "IC2941", "L790", "L2890", "IC3319", "S23790", "L5169", "IC3819", "L5040", "IC4219", "IC19726", "IC2342", "IC3842", "L6091", "IC2241", "IC2540", "IC121", "IC542", "S86590", "L5389", "L5869", "L869", "IC3719", "L4719", "IC4340", "S86369", "S23769", "IC9264", "L4569", "S203069", "L4890", "IC2919", "IC3740", "S86389", "IC2119", "L569", "IC120", "L4290", "L5094", "L2590", "S61591", "IC2619", "IC19968", "IC4119", "L4869", "L5591", "IC719", "L4669", "IC2142", "IC3643", "L10568", "IC819", "IC3341", "IC2219", "L2769", "IC1941", "L2790", "IC519", "IC4041", "L669", "IC1919", "IC9267", "L2869", "S53369", "L5569", "L590", "IC2319", "L5019", "S86569", "S11790", "IC19965", "L5069", "S85669", "L4269", "L4469", "IC3619", "IC4240", "L691", "L4690", "IC1519", "IC4140", "S61569", "L5969", "S102069", "S203090", "L6069", "IC2841", "S85690", "IC2520", "L5270", "L4745", "L1870", "L6270", "S11970", "L5290", "L1890", "IC4019", "S102091", "IC1841", "S103890", "S53391", "L570", "L891", "S103870", "S11770", "IC3120", "IC2820", "L2570", "IC1543", "IC742", "L5190", "IC19737", "IC4441", "IC3141", "IC4320", "L4991", "L5370", "IC1820", "S11990", "IC2942", "L6170", "L2891", "IC3320", "S23791", "L5170", "IC3820", "L5041", "IC4220", "IC19728", "IC2343", "IC3843", "L4770", "IC2242", "L3920", "IC2541", "IC543", "S86591", "L5390", "L870", "IC3720", "L4720", "IC4341", "S86370", "L5770", "S23770", "IC9268", "L5991", "S203070", "L4891", "L4370", "IC2920", "IC3741", "S86390", "IC2120", "L5095", "L2591", "IC841", "S61592", "IC2620", "IC19972", "IC4120", "L4870", "IC2641", "IC720", "L4670", "IC2642", "IC2143", "L4390", "IC3644", "L10569", "L3941", "IC3342", "IC2220", "L2770", "IC1942", "L4970", "L2791", "IC520", "IC4042", "L670", "IC1920", "IC9271", "L2870", "S53370", "L4491", "L591", "IC2320", "L5020", "S86570", "S11791", "IC19969", "L5070", "S85670", "IC3620", "IC4241", "L4592", "L692", "L4691", "IC1520", "IC4141", "S61570", "L6191", "S102070", "S203091", "IC2842", "S85691", "IC2521", "L5271", "L4791", "L4746", "L1871", "S11971", "L5291", "L1891", "IC4020", "S102092", "IC1842", "S103891", "S53392", "L892", "S103871", "S11771", "IC3121", "L2571", "IC4420", "L5791", "L6291", "IC1544", "IC19749", "IC743", "L5191", "L771", "IC3142", "IC4321", "L5371", "IC1821", "S11991", "IC2943", "L792", "L2892", "IC3321", "S23792", "L5171", "IC3821", "L5042", "IC4221", "IC19730", "IC2344", "IC3844", "IC2243", "IC2542", "IC123", "IC544", "S86592", "L5391", "L871", "IC3721", "L4721", "IC2821", "IC4342", "S86371", "S23771", "IC9272", "L4571", "S203071", "L4892", "IC2921", "IC3742", "S86391", "IC2121", "L571", "IC122", "L4292", "L5096", "L2592", "IC2621", "IC4121", "L4871", "L5593", "IC721", "L4671", "IC2144", "IC3645", "L10570", "IC821", "IC3343", "IC2221", "L2771", "IC1943", "L2792", "IC521", "IC19976", "IC4043", "IC1921", "L2871", "L5571", "L592", "L5021", "S86571", "S11792", "IC19973", "L5071", "S85671", "L4271", "L4471", "IC4242", "L693", "IC1521", "IC4142", "S61571", "L5971", "S102071", "S203092", "IC2843", "S85692", "IC2522", "L5272", "IC1545", "L4747", "L1872", "S11972", "IC4021", "S102093", "S103892", "S103872", "S11772", "IC3122", "L2572", "IC744", "L5192", "IC19743", "IC4443", "IC3143", "IC4322", "L5372", "IC1822", "S11992", "L6172", "L2893", "IC3322", "S23793", "L5172", "IC3822", "L5043", "IC4222", "IC2345", "IC3845", "IC2244", "L3922", "IC2543", "S86593", "L5392", "L671", "L872", "IC3722", "L4722", "IC2822", "S86372", "S23772", "IC545", "S203072", "L4893", "L4372", "IC2922", "IC3743", "S86392", "IC2122", "L572", "L2593", "L5097", "IC2622", "L4872", "IC4122", "IC2643", "IC722", "IC2644", "L10571", "IC3344", "L2772", "IC1944", "L2793", "IC4044", "IC1922", "L2872", "L4493", "L593", "S86572", "S11793", "L5072", "S85672", "IC4243", "L694", "IC1522", "IC4143", "S102072", "IC2844", "L4748", "S11973", "IC4022", "S103893", "L893", "S11773", "L2573", "IC522", "IC3144", "IC4324", "S11993", "L5393", "L4723", "S86373", "IC3744", "IC2123", "L573", "L5098", "IC4123", "IC723", "L2773", "IC1945", "IC4045"};
        done = new ArrayList<>();

        start = new HashMap<>();
        end = new HashMap<>();
        LinkedConnectionsApi api = new LinkedConnectionsApi(InstrumentationRegistry.getTargetContext());
       // api.setCacheEnabled(false);

        for (int i = 0; i < trains.length; i += 20) {
            String train = trains[i];
            while (!free) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Log.d("BENCHMARK", i + "/" + trains.length);
            free = false;
            IrailVehicleRequest r = new IrailVehicleRequest(train, new DateTime(2018, 5, 1, 6, 0));
            start.put(train, DateTime.now().getMillis());
            r.setCallback(TrainIndexBenchmark.this, TrainIndexBenchmark.this, train);

            api.getVehicle(r);

            if (i > 0 && i % 100 == 0) {
                long min = 5000, max = 0, avg = 0;
                for (String t : done) {
                    Duration d = new Duration(start.get(t), end.get(t));
                    long ms = d.getMillis();
                    if (ms < min) {
                        min = ms;
                    }
                    if (ms > max) {
                        max = ms;
                    }
                    avg += ms;
                }
                avg = avg / done.size();
                Log.e("BENCHMARK", "min " + min + " avg " + avg + " max " + max);
            }
        }

        long min = 5000, max = 0, avg = 0;
        for (String train : done) {
            Duration d = new Duration(start.get(train), end.get(train));
            long ms = d.getMillis();
            if (ms < min) {
                min = ms;
            }
            if (ms > max) {
                max = ms;
            }
            avg += ms;
        }
        avg = avg / done.size();
        Log.e("BENCHMARK", "min " + min + " avg " + avg + " max " + max);
    }

    @Override
    public void onErrorResponse(@NonNull Exception e, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        free = true;
        Log.d("BENCHMARK", "ready after " + ms + "ms");
    }

    @Override
    public void onSuccessResponse(@NonNull Vehicle data, Object tag) {
        end.put((String) tag, DateTime.now().getMillis());
        done.add((String) tag);
        Duration d = new Duration(start.get(tag), end.get(tag));
        long ms = d.getMillis();
        free = true;
        Log.d("BENCHMARK", "ready after " + ms + "ms");
    }
}

