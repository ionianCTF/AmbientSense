import { Nav } from "./components/Nav";
import { Footer } from "./components/Footer";
import { Home } from "./pages/Home";
import {
  IntroductionPage,
  MethodPage,
  ScreensPage,
  RangingPage,
  EvaluationPage,
  CommunityPage,
  ConclusionPage,
} from "./pages/Subpages";
import { useRoute } from "./router";

export default function App() {
  const path = useRoute();

  return (
    <div className="min-h-screen bg-ivory text-ink">
      <Nav />
      <main>
        {path === "/" ? (
          <Home />
        ) : path === "/introduction" ? (
          <IntroductionPage />
        ) : path === "/method" ? (
          <MethodPage />
        ) : path === "/screens" ? (
          <ScreensPage />
        ) : path === "/ranging" ? (
          <RangingPage />
        ) : path === "/evaluation" ? (
          <EvaluationPage />
        ) : path === "/community" ? (
          <CommunityPage />
        ) : (
          <ConclusionPage />
        )}
      </main>
      <Footer />
    </div>
  );
}
