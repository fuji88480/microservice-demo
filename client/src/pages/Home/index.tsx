import { refreshApi } from "@/api/auth";

export default function Home() {
  return (
    <div>
      <button onClick={() => refreshApi()}>asd</button>
    </div>
  );
}
